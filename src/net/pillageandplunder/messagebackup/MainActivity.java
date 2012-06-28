/* Credits:
 * http://www.anddev.org/write_a_simple_xml_file_in_the_sd_card_using_xmlserializer-t8350.html
 * http://stackoverflow.com/questions/3012287/how-to-read-mms-data-in-android
 */

package net.pillageandplunder.messagebackup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;

import org.xmlpull.v1.XmlSerializer;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.support.v4.app.NavUtils;

public class MainActivity extends Activity {
	private static final String TAG = "MessageBackup";
	private static final Uri CONVERSATIONS_URI = Uri.parse("content://mms-sms/conversations/");
	private static final Uri SMS_URI = Uri.parse("content://sms/");
	private static final String[] SMS_COLUMNS = new String[] { "address", "person", "date", "type",
			"subject", "body" };
	private static final Uri MMS_URI = Uri.parse("content://mms/");
	private static final String[] MMS_COLUMNS = new String[] { "date" };
	private static final Uri MMS_PART_URI = Uri.parse("content://mms/part");
	private static final String[] MMS_PART_COLUMNS = new String[] { "_id", "ct", "_data", "text" };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	public void startBackup(View view) {
		ProgressBar mainProgressBar = (ProgressBar) this.findViewById(R.id.progressBar1);
		ProgressBar subProgressBar = (ProgressBar) this.findViewById(R.id.progressBar2);
		mainProgressBar.setProgress(0);
		subProgressBar.setProgress(0);

		File newxmlfile = new File(Environment.getExternalStorageDirectory() + "/messages.xml");
		try {
			newxmlfile.createNewFile();
		} catch (IOException e) {
			Log.e("IOException", "exception in createNewFile() method");
		}
		FileOutputStream fileos = null;
		try {
			fileos = new FileOutputStream(newxmlfile);
		} catch (FileNotFoundException e) {
			Log.e("FileNotFoundException", "can't create FileOutputStream");
		}
		XmlSerializer serializer = Xml.newSerializer();

		try {
			serializer.setOutput(fileos, "UTF-8");

			// Write <?xml declaration with encoding (if encoding not null) and
			// standalone flag (if standalone not null)
			serializer.startDocument(null, Boolean.valueOf(true));
			// set indentation option
			serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
			serializer.startTag(null, "conversations");

			Cursor cursor = getContentResolver().query(CONVERSATIONS_URI,
					new String[] { "thread_id", "date" }, null, null, null);

			Log.i(TAG, "Number of conversations: " + cursor.getCount());
			mainProgressBar.setMax(cursor.getCount());
			if (cursor != null && cursor.moveToFirst()) {
				do {
					String threadId = cursor.getString(0);
					String date = cursor.getString(1);
					if (threadId == null) {
						continue;
					}
					Log.i(TAG, "Conversation: " + threadId);

					serializer.startTag(null, "conversation");
					serializer.attribute(null, "thread_id", threadId);
					if (date != null) {
						serializer.attribute(null, "date", date);
					}

					Cursor cursor2 = getContentResolver().query(
							Uri.withAppendedPath(CONVERSATIONS_URI, threadId),
							new String[] { "_id", "ct_t" }, null, null, null);
					if (cursor2 != null && cursor2.moveToFirst()) {
						subProgressBar.setProgress(0);
						subProgressBar.setMax(cursor2.getCount());
						do {
							String id = cursor2.getString(0);
							String ct_t = cursor2.getString(1);
							if (ct_t != null) {
								addMMS(serializer, id);
							} else {
								addSMS(serializer, id);
							}
							subProgressBar.incrementProgressBy(1);
						} while (cursor2.moveToNext());
					}
					serializer.endTag(null, "conversation");

					mainProgressBar.incrementProgressBy(1);
				} while (cursor.moveToNext());
			}

			serializer.endTag(null, "conversations");
			serializer.endDocument();
			// write xml data into the FileOutputStream
			serializer.flush();
			// finally we close the file stream
			fileos.close();
		} catch (IllegalArgumentException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} catch (IllegalStateException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
	}

	private void addMMS(XmlSerializer serializer, String id) {
		try {
			Cursor cursor = getContentResolver().query(MMS_URI, MMS_COLUMNS, "_id = " + id, null,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				serializer.startTag(null, "mms");
				serializer.attribute(null, "_id", id);
				String address = getMMSAddress(id);
				if (address != null) {
					serializer.attribute(null, "address", address);
				}
			}
			for (int i = 0; i < cursor.getColumnCount(); i++) {
				String value = cursor.getString(i);
				if (value != null) {
					serializer.attribute(null, cursor.getColumnName(i), value);
				}
			}

			Cursor cursor2 = getContentResolver().query(MMS_PART_URI, MMS_PART_COLUMNS,
					"mid = " + id, null, null);
			if (cursor2 != null && cursor2.moveToFirst()) {
				do {
					serializer.startTag(null, "part");
					String partId = cursor2.getString(0);
					serializer.attribute(null, "id", partId);

					String type = cursor2.getString(1);
					if ("text/plain".equals(type)) {
						serializer.attribute(null, "type", type);
						
						String data = cursor2.getString(2);
						String body;
						if (data != null) {
							// implementation of this method below
							body = getMMSText(partId);
						} else {
							body = cursor2.getString(3);
						}
						serializer.attribute(null, "body", body);
					} else if ("image/jpeg".equals(type) || "image/bmp".equals(type)
							|| "image/gif".equals(type) || "image/jpg".equals(type)
							|| "image/png".equals(type)) {

						Bitmap bitmap = getMMSImage(partId);

						ByteArrayOutputStream bytes = new ByteArrayOutputStream();
						if ("image/jpeg".equals(type)) {
							serializer.attribute(null, "type", "image/jpeg");
							bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
						}
						else {
							serializer.attribute(null, "type", "image/png");
							bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
						}
						byte[] image = bytes.toByteArray();
						serializer.attribute(null, "data",
								Base64.encodeToString(image, Base64.NO_WRAP));
					} else {
						Log.i(TAG, "Unknown content type: " + type);
						serializer.attribute(null, "type", type);
						//serializer.attribute(null, "data", getMMSData(partId));
					}
					serializer.endTag(null, "part");
				} while (cursor2.moveToNext());
			}

			serializer.endTag(null, "mms");
		} catch (IllegalArgumentException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} catch (IllegalStateException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
	}

	private String getMMSText(String partId) {
		InputStream is = null;
		StringBuilder sb = new StringBuilder();
		try {
			is = getContentResolver().openInputStream(Uri.withAppendedPath(MMS_PART_URI, partId));
			if (is != null) {
				InputStreamReader isr = new InputStreamReader(is, "UTF-8");
				BufferedReader reader = new BufferedReader(isr);
				String temp = reader.readLine();
				while (temp != null) {
					sb.append(temp);
					temp = reader.readLine();
				}
			}
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					Log.e(TAG, Log.getStackTraceString(e));
				}
			}
		}
		return sb.toString();
	}

	private Bitmap getMMSImage(String partId) {
		InputStream is = null;
		Bitmap bitmap = null;
		try {
			is = getContentResolver().openInputStream(Uri.withAppendedPath(MMS_PART_URI, partId));
			bitmap = BitmapFactory.decodeStream(is);
		} catch (IOException e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return bitmap;
	}

	private String getMMSData(String partId) {
		InputStream is = null;
		try {
			is = getContentResolver().openInputStream(Uri.withAppendedPath(MMS_PART_URI, partId));
		} catch (FileNotFoundException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

		// this is storage overwritten on each iteration with bytes
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		// we need to know how may bytes were read to write them to the
		// byteBuffer
		int len = 0;
		try {
			while ((len = is.read(buffer)) != -1) {
				byteBuffer.write(buffer, 0, len);
			}
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}

		// and then we can return your byte array.
		return Base64.encodeToString(byteBuffer.toByteArray(), Base64.NO_WRAP);
	}

	private String getMMSAddress(String id) {
		Cursor cursor = getContentResolver().query(Uri.withAppendedPath(MMS_URI, id + "/addr"),
				null, "msg_id=" + id, null, null);
		String name = null;
		if (cursor != null && cursor.moveToFirst()) {
			do {
				String number = cursor.getString(cursor.getColumnIndex("address"));
				if (number != null) {
					try {
						Long.parseLong(number.replace("-", ""));
						name = number;
					} catch (NumberFormatException nfe) {
						if (name == null) {
							name = number;
						}
					}
				}
			} while (cursor.moveToNext());
		}
		if (cursor != null) {
			cursor.close();
		}
		return name;
	}

	private void addSMS(XmlSerializer serializer, String id) {
		try {
			Cursor cursor = getContentResolver().query(SMS_URI, SMS_COLUMNS, "_id = " + id, null,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				serializer.startTag(null, "sms");
				serializer.attribute(null, "_id", id);
				for (int i = 0; i < cursor.getColumnCount(); i++) {
					String value = cursor.getString(i);
					if (value != null) {
						serializer.attribute(null, cursor.getColumnName(i), value);
					}
				}
				serializer.endTag(null, "sms");
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} catch (IllegalStateException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
	}
}
