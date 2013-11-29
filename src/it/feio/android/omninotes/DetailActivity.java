/*******************************************************************************
 * Copyright 2013 Federico Iosue (federico.iosue@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.feio.android.omninotes;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.joda.time.DateTime;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.doomonafireball.betterpickers.calendardatepicker.CalendarDatePickerDialog;
import com.doomonafireball.betterpickers.radialtimepicker.RadialPickerLayout;
import com.doomonafireball.betterpickers.radialtimepicker.RadialTimePickerDialog;
import com.neopixl.pixlui.components.textview.TextView;

import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.AttachmentAdapter;
import it.feio.android.omninotes.models.ExpandableHeightGridView;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.receiver.AlarmReceiver;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.date.DateHelper;
import it.feio.android.omninotes.utils.date.DatePickerFragment;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.utils.date.TimePickerFragment;
import it.feio.android.omninotes.R;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * An activity representing a single Item detail screen. This activity is only
 * used on handset devices. On tablet-size devices, item details are presented
 * side-by-side with a list of items in a {@link ItemListActivity}.
 * <p>
 * This activity is mostly just a 'shell' activity containing nothing more than
 * a {@link ItemDetailFragment}.
 */
public class DetailActivity extends BaseActivity {

	private static final int TAKE_PHOTO = 1;
	private static final int GALLERY = 2;
	
	private SherlockFragmentActivity mActivity;
	
	private Note note;
	private ImageView reminder, reminder_delete;
	private TextView datetime;
	private String alarmDate = "", alarmTime = "";
	private String dateTimeText = "";
	private long alarmDateTime = -1;
	public Uri imageUri;
	private AttachmentAdapter mAttachmentAdapter;
	private ExpandableHeightGridView mGridView;
	private List<Attachment> attachmentsList = new ArrayList<Attachment>();
	private AlertDialog attachmentDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_detail);
		
		mActivity = this;

		// Show the Up button in the action bar.
		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		// Note initialization
		initNote();
		
		// Views initialization
		initViews();
	}

	private void initViews() {
		
		// Initialzation of gridview for images
		mGridView = (ExpandableHeightGridView) findViewById(R.id.gridview);
	    mGridView.setAdapter(mAttachmentAdapter);
	    mGridView.setExpanded(true);
	    
	    // Click events for images in gridview (zooms image)
	    mGridView.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	        	Uri uri = ((Attachment)parent.getAdapter().getItem(position)).getUri();
	            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
	            startActivity(intent);
	        }
	    });
	    // Long click events for images in gridview	(removes image)
	    mGridView.setOnItemLongClickListener(new OnItemLongClickListener() {			
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View v, final int position, long id) {
				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mActivity);
				alertDialogBuilder.setMessage(R.string.confirm_image_deletion).setCancelable(false)
						.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int id) {
								attachmentsList.remove(position);
								mAttachmentAdapter.notifyDataSetChanged();
							}
						}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
				AlertDialog alertDialog = alertDialogBuilder.create();
				alertDialog.show();
//				imgSrcDialog.dismiss();
				return false;
			}
		});
		
	   
	    // Preparation for reminder icon
		reminder = (ImageView) findViewById(R.id.reminder);
		reminder.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Timepicker will be automatically called after date is inserted by user
//				showDatePickerDialog(v);
				showDateTimeSelectors();
				
			}
		});

		reminder_delete = (ImageView) findViewById(R.id.reminder_delete);
		reminder_delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				alarmDate = "";
				alarmTime = "";
				alarmDateTime = -1;
				datetime.setText("");
				reminder_delete.setVisibility(View.INVISIBLE);
			}
		});
		// Checks if an alarm is set to show deletion icon
		if (dateTimeText.length() > 0)
			reminder_delete.setVisibility(View.VISIBLE);
		
		datetime = (TextView) findViewById(R.id.datetime);
		datetime.setText(dateTimeText);
	}

	
	/**
	 *  Show date and time pickers
	 */
	protected void showDateTimeSelectors() {
		final DateTime now = DateTime.now();
		CalendarDatePickerDialog mCalendarDatePickerDialog = CalendarDatePickerDialog.newInstance(new CalendarDatePickerDialog.OnDateSetListener() {
			
			@Override
			public void onDateSet(CalendarDatePickerDialog dialog, int year,
					int monthOfYear, int dayOfMonth) {
//				now.withYear(year);
//				now.withMonthOfYear(monthOfYear);
//				now.withDayOfMonth(dayOfMonth);
				alarmDate = DateHelper.onDateSet(year, monthOfYear, dayOfMonth, Constants.DATE_FORMAT_SHORT_DATE);
				Log.d(Constants.TAG, "Date set");
				RadialTimePickerDialog mRadialTimePickerDialog = RadialTimePickerDialog.newInstance(new RadialTimePickerDialog.OnTimeSetListener() {
					
					@Override
					public void onTimeSet(RadialPickerLayout view,
							int hourOfDay, int minute) {
//						now.withHourOfDay(hourOfDay);
//						now.withMinuteOfHour(minute);
						
						// Creation of string rapresenting alarm time		
						alarmTime = DateHelper.onTimeSet(hourOfDay, minute,
								Constants.DATE_FORMAT_SHORT_TIME);
						datetime.setText(getString(R.string.alarm_set_on) + " " + alarmDate
								+ " " + getString(R.string.at_time) + " " + alarmTime);
				
						// Setting alarm time in milliseconds
						alarmDateTime = DateHelper.getLongFromDateTime(alarmDate,
								Constants.DATE_FORMAT_SHORT_DATE, alarmTime,
								Constants.DATE_FORMAT_SHORT_TIME).getTimeInMillis();
						
						// Shows icon to remove alarm
						reminder_delete.setVisibility(View.VISIBLE);
						
						Log.d(Constants.TAG, "Time set");						
					}
				}, now.getHourOfDay(), now.getMinuteOfHour(), true);
				mRadialTimePickerDialog.show(getSupportFragmentManager(), "fragment_time_picker_name");
			}

		}, now.getYear(), now.getMonthOfYear(), now.getDayOfMonth());
		mCalendarDatePickerDialog.show(getSupportFragmentManager(), "fragment_date_picker_name");
		
	}

	
	private void initNote() {
		note = (Note) getIntent().getParcelableExtra(Constants.INTENT_NOTE);

		if (note.get_id() != 0) {
			((EditText) findViewById(R.id.title)).setText(note.getTitle());
			((EditText) findViewById(R.id.content)).setText(note.getContent());
			((TextView) findViewById(R.id.creation))
					.append(getString(R.string.creation) + " "
							+ note.getCreationShort());
			((TextView) findViewById(R.id.last_modification))
					.append(getString(R.string.last_update) + " "
							+ note.getLastModificationShort());
			if (note.getAlarm() != null) {
				dateTimeText = initAlarm(Long.parseLong(note.getAlarm()));
			}
			
			// If a new note is being edited the keyboard will not be shown on activity start
//			getWindow().setSoftInputMode(
//					WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
		attachmentsList = note.getAttachmentsList();
		mAttachmentAdapter = new AttachmentAdapter(mActivity, attachmentsList);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_share).setVisible(true);
		menu.findItem(R.id.menu_attachment).setVisible(true);
		menu.findItem(R.id.menu_delete).setVisible(true);
		menu.findItem(R.id.menu_discard_changes).setVisible(true);

		boolean archived = note.isArchived();
		menu.findItem(R.id.menu_archive).setVisible(!archived);
		menu.findItem(R.id.menu_unarchive).setVisible(archived);

		return super.onPrepareOptionsMenu(menu);
	}

	private boolean goHome() {
		NavUtils.navigateUpFromSameTask(this);
		if (prefs.getBoolean("settings_enable_animations", true)) {
			overridePendingTransition(R.animator.slide_left,
					R.animator.slide_right);
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		saveNote(null);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			saveNote(null);
			break;
		case R.id.menu_share:
			shareNote();
			break;
		case R.id.menu_archive:
			saveNote(true);
			break;
		case R.id.menu_unarchive:
			saveNote(false);
			break;
		case R.id.menu_attachment:			
			this.attachmentDialog = showAttachmentDialog(); 
			break;
		case R.id.menu_delete:
			deleteNote();
			break;
		case R.id.menu_discard_changes:
			goHome();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private AlertDialog showAttachmentDialog() {
		AlertDialog.Builder attachmentDialog = new AlertDialog.Builder(this);
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.attachment_dialog,
				(ViewGroup) findViewById(R.id.layout_root));
		attachmentDialog.setView(layout);
		android.widget.TextView cameraSelection = (android.widget.TextView) layout.findViewById(R.id.camera);
		cameraSelection.setOnClickListener(new AttachmentOnClickListener());
		android.widget.TextView gallerySelection = (android.widget.TextView) layout.findViewById(R.id.gallery);
		gallerySelection.setOnClickListener(new AttachmentOnClickListener());
		return attachmentDialog.show();
	}
	
	
	/**
	 * Manages clicks on attachment dialog
	 */
	private class AttachmentOnClickListener implements OnClickListener {

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.camera:
				ContentValues values = new ContentValues();
				values.put(MediaStore.Images.Media.TITLE, "New Picture");
				values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
				imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
				startActivityForResult(intent, TAKE_PHOTO);
				attachmentDialog.dismiss();
				break;
			case R.id.gallery:
				Intent galleryIntent;
				if (Build.VERSION.SDK_INT >= 19){
					galleryIntent = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				} else {
					galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
					galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
				}
				galleryIntent.setType("image/*");
				startActivityForResult(galleryIntent, GALLERY);
				attachmentDialog.dismiss();
				break;
			}

		}
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// Fetch uri from activities, store into adapter and refresh adapter
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case TAKE_PHOTO:
					attachmentsList.add(new Attachment(imageUri));
					mAttachmentAdapter.notifyDataSetChanged();
					break;
				case GALLERY:
					attachmentsList.add(new Attachment(intent.getData()));
					mAttachmentAdapter.notifyDataSetChanged();
					break;
			}
		}
	}


	
	
	private void deleteNote() {

		// Confirm dialog creation
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder
				.setMessage(R.string.delete_note_confirmation)
				.setPositiveButton(R.string.confirm,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int id) {
								// Simply return to the previous
								// activity/fragment if it was a new note
								if (getIntent().getStringExtra(
										Constants.INTENT_KEY) == null) {
									goHome();
									return;
								}

								// Create note object
								int _id = Integer.parseInt(getIntent()
										.getStringExtra(Constants.INTENT_KEY));
								Note note = new Note();
								note.set_id(_id);

								// Deleting note using DbHelper
								DbHelper db = new DbHelper(
										getApplicationContext());
								db.deleteNote(note);

								// Informs the user about update
								Log.d(Constants.TAG, "Deleted note with id '"
										+ _id + "'");
								showToast(
										getResources().getText(
												R.string.note_deleted),
										Toast.LENGTH_SHORT);
								goHome();
								return;
							}
						})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int id) {
							}
						});
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}

	/**
	 * Save new notes, modify them or archive
	 * 
	 * @param archive
	 *            Boolean flag used to archive note
	 */
	private void saveNote(Boolean archive) {
		
		// Get old reminder to check later if is changed
		String oldAlarm = note.getAlarm();
		
		// Changed fields
		String title = ((EditText) findViewById(R.id.title)).getText()
				.toString();
		String content = ((EditText) findViewById(R.id.content)).getText()
				.toString();

		Note noteEdited = note;
		if (noteEdited != null) {
			note = noteEdited;
		} else {
			note = new Note();
		}

		// Check if some text has ben inserted or is an empty note
		if ((title + content).length() == 0) {
			Log.d(Constants.TAG, "Empty note not saved");
			showToast(getResources().getText(R.string.empty_note_not_saved),
					Toast.LENGTH_SHORT);
			goHome();
			return;
		}

		// Logging operation
		Log.d(Constants.TAG, "Saving new note titled: " + title + " (archive var: " + archive + ")");

		note.set_id(note.get_id());
		note.setTitle(title);
		note.setContent(content);
		note.setArchived(archive != null ? archive : note.isArchived());
		note.setAlarm(alarmDateTime != -1 ? String.valueOf(alarmDateTime) : null);
		note.setAttachmentsList(attachmentsList);

		// Saving changes to the note
		DbHelper db = new DbHelper(this);
		note = db.updateNote(note);
		
		// Advice of update
		showToast(getResources().getText(R.string.note_updated),
				Toast.LENGTH_SHORT);

		// Saves reminder if is not in actual 
		if (note.getAlarm() != null && !note.getAlarm().equals(oldAlarm)) {				
				setAlarm();
		}
		
		// Go back on stack
		goHome();
	}

	
	private void setAlarm() {
		Intent intent = new Intent(this, AlarmReceiver.class);
		intent.putExtra(Constants.INTENT_NOTE, note);
		PendingIntent sender = PendingIntent.getBroadcast(this, Constants.INTENT_ALARM_CODE, intent,
				PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, alarmDateTime, sender);		
	}
	

	private void shareNote() {
		// Changed fields
		String title = ((EditText) findViewById(R.id.title)).getText()
				.toString();
		String content = ((EditText) findViewById(R.id.content)).getText()
				.toString();

		// Check if some text has ben inserted or is an empty note
		if ((title + content).length() == 0) {
			Log.d(Constants.TAG, "Empty note not shared");
			showToast(getResources().getText(R.string.empty_note_not_shared),
					Toast.LENGTH_SHORT);
			return;
		}

		// Definition of shared content
		String text = title + System.getProperty("line.separator") + content
				+ System.getProperty("line.separator")
				+ System.getProperty("line.separator")
				+ getResources().getString(R.string.shared_content_sign);

		// Prepare sharing intent
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		// shareIntent.setType("*/*");
		shareIntent.putExtra(Intent.EXTRA_TEXT, text);
		startActivity(Intent.createChooser(shareIntent, getResources()
				.getString(R.string.share_message_chooser)));
	}

	
//	public void showDatePickerDialog(View v) {
//		DatePickerFragment newFragment = new DatePickerFragment();
//		newFragment.show(getSupportFragmentManager(), "datePicker");
//	}
//
//	/**
//	 * Shows time picker to set alarm
//	 * 
//	 * @param v
//	 */
//	private void showTimePickerDialog(View v) {
//		TimePickerFragment newFragment = new TimePickerFragment();
//		newFragment.show(getSupportFragmentManager(), Constants.TAG);
//	}
//
//
//	@Override
//	public void onDateSet(DatePicker v, int year, int month, int day) {
//		alarmDate = DateHelper.onDateSet(year, month, day,
//				Constants.DATE_FORMAT_SHORT_DATE);
//		showTimePickerDialog(v);
//	}
//
//	@Override
//	public void onTimeSet(TimePicker v, int hour, int minute) {
//		
//		// Creation of string rapresenting alarm time		
//		alarmTime = DateHelper.onTimeSet(hour, minute,
//				Constants.DATE_FORMAT_SHORT_TIME);
//		datetime.setText(getString(R.string.alarm_set_on) + " " + alarmDate
//				+ " " + getString(R.string.at_time) + " " + alarmTime);
//
//		// Setting alarm time in milliseconds
//		alarmDateTime = DateHelper.getLongFromDateTime(alarmDate,
//				Constants.DATE_FORMAT_SHORT_DATE, alarmTime,
//				Constants.DATE_FORMAT_SHORT_TIME).getTimeInMillis();
//		
//		// Shows icon to remove alarm
//		reminder_delete.setVisibility(View.VISIBLE);
//	}
	
	
	/**
	 * Used to set acual alarm state when initializing a note to be edited
	 * @param alarmDateTime
	 * @return
	 */
	private String initAlarm(long alarmDateTime) {
		this.alarmDateTime = alarmDateTime;
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(alarmDateTime);
		alarmDate = DateHelper.onDateSet(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
				Constants.DATE_FORMAT_SHORT_DATE);
		alarmTime = DateHelper.onTimeSet(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
				Constants.DATE_FORMAT_SHORT_TIME);
		String dateTimeText = getString(R.string.alarm_set_on) + " " + alarmDate
				+ " " + getString(R.string.at_time) + " " + alarmTime;
		return dateTimeText;
	}
	
	
	public String getAlarmDate(){
		return alarmDate;
	}
	
	public String getAlarmTime(){
		return alarmTime;
	}



}
