/*
 * Copyright (C) 2013 Felix Bechstein
 * 
 * This file is part of Clipboard Beam.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.clipboardbeam;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.ClipboardManager;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements CreateNdefMessageCallback {

	private static final String TAG = "cbbeam";
	private static final Pattern P = Pattern.compile("passcode-([a-z0-9]*)");
	private static final String INGRESS_PACKAGE = "com.nianticproject.ingress";

	EditText mEt;
	NfcAdapter mNfcAdapter;
	private ShareActionProvider mShareActionProvider;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mEt = (EditText) findViewById(R.id.editText1);
		if (savedInstanceState != null) {
			mEt.setText(savedInstanceState.getString("text"));
		} else {
			CharSequence text = null;
			ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			Intent i = getIntent();
			String action = i.getAction();
			if (action != null
					&& action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
				// got NFC, save to mET & CB
				text = null;
				Parcelable[] rawMsgs = i
						.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
				if (rawMsgs != null && rawMsgs.length > 0) {
					NdefMessage msg = (NdefMessage) rawMsgs[0];
					NdefRecord[] records = msg.getRecords();
					if (records != null && records.length > 0) {
						try {
							text = parseNdef(records[0]);
						} catch (UnsupportedEncodingException e) {
							Log.e(TAG, "encoding error", e);
							Toast.makeText(this, R.string.error,
									Toast.LENGTH_LONG).show();
							text = null;
						}
					}
				}
				if (text != null) {
					text = text.toString().trim();
				}
				cb.setPrimaryClip(new ClipData("text",
						new String[] { "text/plain" }, new Item(text)));
				Toast.makeText(this, R.string.text_saved, Toast.LENGTH_LONG)
						.show();
			} else if (action != null && action.equals(Intent.ACTION_VIEW)
					&& i.getData() != null) {
				text = i.getDataString();
				Matcher m = P.matcher(text);
				if (m.find()) {
					text = m.group(1);
				}
				cb.setPrimaryClip(new ClipData("text",
						new String[] { "text/plain" }, new Item(text)));
				Toast.makeText(this, R.string.text_saved, Toast.LENGTH_LONG)
						.show();
			} else if (cb.hasPrimaryClip()) {
				text = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
			}
			if (text != null) {
				mEt.setText(text.toString().trim());
			}
		}

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null) {
			// point to NFC settings
			Toast.makeText(this, R.string.activate_nfc, Toast.LENGTH_LONG)
					.show();
			startActivity(new Intent(
					android.provider.Settings.ACTION_WIRELESS_SETTINGS));
		} else {
			mNfcAdapter.setNdefPushMessageCallback(this, this, this);
		}

		mEt.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(final CharSequence s, final int start,
					final int before, final int count) {
				// ignore
			}

			@Override
			public void beforeTextChanged(final CharSequence s,
					final int start, final int count, final int after) {
				// ignore
			}

			@Override
			public void afterTextChanged(final Editable s) {
				setShareIntent();
			}
		});

		setShareIntent();
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		outState.putString("text", mEt.getText().toString());
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		MenuItem item = menu.findItem(R.id.menu_share);
		mShareActionProvider = (ShareActionProvider) item.getActionProvider();
		setShareIntent();
		if (getPackageManager().getLaunchIntentForPackage(INGRESS_PACKAGE) == null) {
			menu.removeItem(R.id.menu_ingress);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_ingress:
			startActivity(getPackageManager().getLaunchIntentForPackage(
					INGRESS_PACKAGE));
			return true;
		case R.id.menu_info:
			Builder b = new Builder(this);
			b.setCancelable(true);
			b.setIcon(R.drawable.ic_launcher);
			b.setTitle(R.string.info);
			b.setPositiveButton(android.R.string.ok, null);
			SpannableString s = new SpannableString(getString(
					R.string.info_long, getString(R.string.app_name),
					getString(R.string.link_gpl3),
					getString(R.string.link_github)));
			Linkify.addLinks(s, Linkify.WEB_URLS);
			b.setMessage(s);
			AlertDialog d = b.create();
			d.show();
			TextView tv = (TextView) d.findViewById(android.R.id.message);
			if (tv != null) {
				tv.setMovementMethod(LinkMovementMethod.getInstance());
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public NdefMessage createNdefMessage(final NfcEvent event) {
		byte[] langBytes = Locale.getDefault().getLanguage()
				.getBytes(Charset.forName("US-ASCII"));
		byte[] textBytes = mEt.getText().toString().trim()
				.getBytes(Charset.forName("UTF-8"));
		char status = (char) (langBytes.length);
		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length,
				textBytes.length);

		return new NdefMessage(new NdefRecord[] {
				new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT,
						new byte[0], data),
				NdefRecord.createApplicationRecord(getPackageName()) });
	}

	private CharSequence parseNdef(final NdefRecord r)
			throws UnsupportedEncodingException {
		short t = r.getTnf();
		byte[] type = r.getType();
		if (t == NdefRecord.TNF_WELL_KNOWN
				&& Arrays.equals(type, NdefRecord.RTD_TEXT)) {
			byte[] payload = r.getPayload();
			byte status = payload[0];
			String enc = ((status & 0x80) == 0) ? "UTF-8" : "UTF-16";
			int lcl = status & 0x3f;
			return new String(payload, lcl + 1, payload.length - lcl - 1, enc);
		} else {
			Log.w(TAG, "unknown TNF: " + new String(type));
			return new String(r.getPayload());
		}
	}

	private void setShareIntent() {
		if (mShareActionProvider != null) {
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain");
			i.putExtra(Intent.EXTRA_TEXT, mEt.getText().toString().trim());
			mShareActionProvider.setShareIntent(i);
		}
	}
}
