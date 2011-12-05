/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.activity;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.dataservice.EventInstance;
import com.morphoss.acal.dataservice.WriteableEventInstance;

/**
 * <p>
 * Adapter for providing views for events.
 * </p>
 * 
 * @author Morphoss Ltd
 * 
 */
public class EventListAdapter extends BaseAdapter implements OnClickListener, ListAdapter {

	/**
	 * <p>
	 * Presently this view is only supported in month view. If we decide to extend this view further we should
	 * create An interface for providing callbacks.
	 * </p>
	 */ 
	private MonthView context;
	private AcalDateTime viewDate;
	private AcalDateTime viewDateEnd;
	public static final String TAG = "aCal EventListAdapter";
	private volatile boolean clickEnabled = true;
	public static final int CONTEXT_EDIT = 0;
	public static final int CONTEXT_DELETE_ALL = 0x10000;
	public static final int CONTEXT_DELETE_JUSTTHIS = 0x20000;
	public static final int CONTEXT_DELETE_FROMNOW = 0x30000;
	public static final int CONTEXT_COPY = 0x40000;

	private ArrayList<EventInstance> dayEvents = null;
	
//	private SharedPreferences prefs;	

	/**
	 * <p>Create a new adaptor with the attributes provided. The date range provided specifies the date range that all
	 * events provided by this adapter fall within. This may change as the calling class may have the required events
	 * in memory already.</p>
	 */
	public EventListAdapter(MonthView monthview, AcalDateTime date) {
		this.context = monthview;
		viewDate = date.clone();
		viewDate.applyLocalTimeZone();
		viewDate.setDaySecond(0);
		viewDateEnd = AcalDateTime.addDays(viewDate, 1);

		dayEvents = context.getEventsForDay(viewDate);
	}

	/**
	 * <p>Returns the number of elements in this adapter.</p>
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return dayEvents.size();
	}

	/**
	 * <p>Returns the event at specified the position in this adapter or null if position is invalid.</p> 
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public Object getItem(int position) {
		return dayEvents.get(position);
	}

	/**
	 * <p>Returns the id associated with the event at specified position. Currently not implemented (i.e. returns position)</p>
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		return position;
	}


	/**
	 * <p>Returns the view associated with the event at the specified position. Currently, views
	 * do not respond to any events.</p> 
	 * 
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		LinearLayout rowLayout;

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		rowLayout = (LinearLayout) inflater.inflate(R.layout.event_list_item, parent, false);

		TextView title = (TextView) rowLayout.findViewById(R.id.EventListItemTitle);
		TextView time = (TextView) rowLayout.findViewById(R.id.EventListItemTime);
		TextView location = (TextView) rowLayout.findViewById(R.id.EventListItemLocation);
		
		LinearLayout sideBar = (LinearLayout) rowLayout.findViewById(R.id.EventListItemColorBar);

		EventInstance event = dayEvents.get(position);
		if ( event == null ) return rowLayout;
		
		final boolean isPending = event.isPending();
		if (isPending) {
			sideBar.setBackgroundColor(event.getCollection().getColour()|0xa0000000); title.setTextColor(event.getCollection().getColour()|0xa0000000);
			((LinearLayout) rowLayout.findViewById(R.id.EventListItemText)).setBackgroundColor(0x44000000);
		}
		else {
			rowLayout.findViewById(R.id.EventListItemIcons).setBackgroundColor(event.getCollection().getColour());
			sideBar.setBackgroundColor(event.getCollection().getColour()); 
			title.setTextColor(event.getCollection().getColour());
		}

		title.setText((event.getSummary() == null  || event.getSummary().length() <= 0 ) ? "Untitled" : event.getSummary());

		if ( !event.getAlarms().isEmpty() ) {
			ImageView alarmed = (ImageView) rowLayout.findViewById(R.id.EventListItemAlarmBell);
			alarmed.setVisibility(View.VISIBLE);
			if ( ! event.getCollection().alarmsEnabled() ) alarmed.setBackgroundColor(0xb0ffffff);
		}
		if ( !event.isSingleInstance() ) {
			ImageView repeating = (ImageView) rowLayout.findViewById(R.id.EventListItemRepeating);
			repeating.setVisibility(View.VISIBLE);
		}
		
		time.setText(event.getTimeText(context, viewDate.getEpoch(), viewDateEnd.getEpoch(),
					MonthView.prefs.getBoolean(context.getString(R.string.prefTwelveTwentyfour), false))
					+ (isPending ? " (saving)" : "") );

		if (event.getLocation() != null && event.getLocation().length() > 0 )
			location.setText(event.getLocation());
		else
			location.setHeight(2);

		rowLayout.setTag(event);
		rowLayout.setOnTouchListener(this.context);
		rowLayout.setOnClickListener(this);

		// 'final' so we can refer to it below
		final boolean repeats = ! event.isSingleInstance();

		//add context menu
		this.context.registerForContextMenu(rowLayout);
		rowLayout.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
		

			@Override
			public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
				menu.setHeaderTitle("Event");
				if ( !isPending ) menu.add(0, position, 0, context.getString(R.string.Edit));

				menu.add(0, CONTEXT_COPY + position, 0, context.getString(R.string.newEventFromThis));
				if (repeats) {
					menu.add(0,CONTEXT_DELETE_ALL+position,0, context.getString(R.string.deleteAllInstances));
					menu.add(0,CONTEXT_DELETE_JUSTTHIS+position, 0, context.getString(R.string.deleteThisInstance));
					menu.add(0,CONTEXT_DELETE_FROMNOW+position,0, context.getString(R.string.deleteThisAndFuture));
				} else {
					menu.add(0,CONTEXT_DELETE_ALL+position,0, context.getString(R.string.Delete));
				}
			}
		});

		return rowLayout;
	}

	public void setClickEnabled(boolean enabled) {
		this.clickEnabled = enabled;
	}

	@Override
	public void onClick(View arg0) {
		if (clickEnabled) {
			Object tag = arg0.getTag();
			if (tag instanceof EventInstance) {
				//start event activity
				Bundle bundle = new Bundle();
				bundle.putParcelable("EventInstance", (EventInstance)tag);
				Intent eventViewIntent = new Intent(context, EventView.class);
				eventViewIntent.putExtras(bundle);
				context.startActivityForResult(eventViewIntent, MonthView.PICK_TODAY_FROM_EVENT_VIEW);
			}
		} else {
			clickEnabled = true;
		}

	}
	
	public boolean contextClick(MenuItem item) {
		try {
			int id = item.getItemId();
			int action = id & 0xf0000;
			id = id & 0xffff;

			EventInstance sae = (EventInstance)this.getItem(id);
			sae.setOperation(EventInstance.EVENT_OPERATION_EDIT);
			switch( action ) {
				case CONTEXT_COPY:
					sae.setOperation(EventInstance.EVENT_OPERATION_COPY);
				case CONTEXT_EDIT:
					//start EventEdit activity
					Bundle bundle = new Bundle();
					bundle.putParcelable("EventInstance", sae);
					Intent eventViewIntent = new Intent(context, EventEdit.class);
					eventViewIntent.putExtras(bundle);
					context.startActivity(eventViewIntent);
					return true;
				
				case CONTEXT_DELETE_ALL:
					this.context.deleteEvent(viewDate,id,WriteableEventInstance.ACTION_DELETE_ALL);
					return true;

				case CONTEXT_DELETE_JUSTTHIS:
					this.context.deleteEvent(viewDate,id,WriteableEventInstance.ACTION_DELETE_SINGLE);
					return true;

				case CONTEXT_DELETE_FROMNOW:
					this.context.deleteEvent(viewDate,id,WriteableEventInstance.ACTION_DELETE_ALL_FUTURE);
					return true;
			}
			return false;
		}
		catch (ClassCastException e) {
			return false;
		}
		
	}

}
