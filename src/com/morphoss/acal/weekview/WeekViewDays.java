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

package com.morphoss.acal.weekview;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.davacal.SimpleAcalEvent;

public class WeekViewDays extends ImageView {
	
	public static final String TAG = "aCal - WeekViewDays";
	
	private WeekViewActivity context; 
	private AcalDateTime firstVisibleDay;
	
	private SimpleAcalEvent[][] headerTimeTable;
	

	//Drawing vars - easier to set these as class fields than to send them as parmaeters
	//All these vars are used in drawing and are recaculated each time draw() is called and co-ordinates have changed
	
	// These need only be calculated once 
	private int width;				//The current screen width in pixels
	private int TpX;				//The current Screen height in pixels
	private int HSPP;				//Horizontal Seconds per Pixel
	private int HNS;				//the number of visible horizontal seconds
	private int HIH;				//The height of a Horizontal event

	// These ones do change as things move around
	private int PxD;				//The current height of days section
	private int PxH;				//The current height of the Header section
	private int topSec;				//The first second of the day that is visible in main view
	private int scrollx;			//The current (valid) horizontal scroll amount
	private int scrolly;			//The current (valid) vertical scroll amount
	private long currentEpoch;		//The UTC epoch time of 0:00 on the first Visible Day
	private long HST;				//The UTC epoch time of the first visible horizontal second
	private long HET;				//The UTC epoch time of the last visible horizontal second
	private int HDepth;				//The number of horizontal rows
	private int lastMaxX;
	private List<SimpleAcalEvent> HSimpleList;  	//The last list of (simple) events for the header
	private SimpleAcalEvent[][] HTimetable;  			//The last timetable used for the header
	
	private boolean isInitialized = false;	//Set to True once screen dimensions are calculated.

	private Paint	workPaint;
	
	/** Default Constructor */
	public WeekViewDays(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.firstVisibleDay = this.context.getCurrentDate();
	}
	
	/** Default Constructor */
	public WeekViewDays(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.firstVisibleDay = this.context.getCurrentDate();
	} 
	
	/** Default Constructor */
	public WeekViewDays(Context context) {
		super(context);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.firstVisibleDay = this.context.getCurrentDate();
	}
	

	public int getHeaderHeight() {
		return this.PxH; 
	}

	/**
	 * Return the nearest valid 'y' to the one given, ensuring we don't scroll above / below the day.
	 *  
	 * @param y A position we are attempting to scroll to
	 * @return The position closest to that which is reasonable.
	 */
	int checkScrollY(int y) {
		if ( y < -PxH ) return -PxH;
		int max = ((AcalDateTime.SECONDS_IN_DAY/WeekViewActivity.SECONDS_PER_PIXEL) - TpX)+5;
		if ( y > max ) return max;
		return y;
	}

	
	private void drawHeader(Canvas canvas, Paint p) {
		
		//1 Calculate per frame vars
		HST = this.currentEpoch - (scrollx*HSPP);
		HET = HST+HNS;
		
		AcalDateTime startTime = new AcalDateTime().setEpoch(HST).applyLocalTimeZone();
		AcalDateTime endTime = startTime.clone().setEpoch(HET);
		AcalDateRange range = new AcalDateRange(startTime,endTime);
		
		//Get the current timetable
		headerTimeTable = getMultiDayTimeTable(range);
		if (headerTimeTable.length <=0) {	this.PxH =0; return; }
		PxH = HDepth*HIH;
		
		//draw day boxes
		p.setStyle(Paint.Style.STROKE);
		p.setColor(context.getResources().getColor(R.color.WeekViewDayGridBorder));
		canvas.drawRect(0, 0, width, PxH, p);
		for (int curx =0-(WeekViewActivity.DAY_WIDTH-scrollx); curx<=width; curx+= WeekViewActivity.DAY_WIDTH) {
			canvas.drawRect(curx, 0, curx+WeekViewActivity.DAY_WIDTH,PxH,p);
		}
		
		
		for (int i = 0; i<headerTimeTable.length;i++)  {
			boolean hasEvent = false;
			for(int j=0;j < headerTimeTable[i].length && headerTimeTable[i][j] != null;j++) {
				SimpleAcalEvent event = headerTimeTable[i][j];
					drawHorizontal(event, canvas,i);
					hasEvent=true;
			}
			if (!hasEvent) break;
		}
		
	}
	
	public void dimensionsChanged() {
		if ( Constants.LOG_DEBUG )
			Log.d(TAG,"Dimensions may have changed, recalculating...");
		this.isInitialized = false;
	}

	public boolean isInitialized() { return this.isInitialized; }
	private void initialize() {
		//Vars needed for drawing
		width = this.getWidth();
		TpX = this.getHeight();

		//Seconds per pixel min/max values (MAX full day visible MIN 1 pixel per minute)
		WeekViewActivity.SECONDS_PER_PIXEL = Math.max(WeekViewActivity.SECONDS_PER_PIXEL,60);
		WeekViewActivity.SECONDS_PER_PIXEL = Math.min(WeekViewActivity.SECONDS_PER_PIXEL,(86400/TpX)+1);
		
		//Horizontal SecsPerPix and Horizontal Number Visible Seconds and Horizontal Item Height
		HSPP = 86400/WeekViewActivity.DAY_WIDTH;
		HNS = width*HSPP;
		HIH = (int)WeekViewActivity.FULLDAY_ITEM_HEIGHT;
		
		//Scroll info
		scrolly = context.getScrollY();
		scrollx = context.getScrollX();
		
		workPaint = new Paint();
		workPaint.setStyle(Paint.Style.FILL);
		workPaint.setColor(context.getResources().getColor(R.color.WeekViewDayGridWorkTimeBG));

		this.isInitialized = true;
		context.refresh();
		
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		
		//First check that we can draw anything at all
		if (this.getWidth() == 0) return;
		if (this.getHeight() == 0) return;
		if (this.isInEditMode()) return;	//can't draw in edit mode
		
		if (!this.isInitialized) { this.initialize(); return; }
		
		//calculate variables that may change from frame to frame
		scrolly = context.getScrollY();
		scrollx = context.getScrollX();
		this.currentEpoch = firstVisibleDay.getEpoch();
		
		Paint p = new Paint();
		drawBackground(canvas);
		
		//need to draw header first. Drawing the header also adjusts the y value and height.
		this.drawHeader(canvas,p);
		PxD = TpX - PxH;
		topSec = scrolly * WeekViewActivity.SECONDS_PER_PIXEL;

		drawGrid(canvas,p);
		
		int dayX = (int)(0-WeekViewActivity.DAY_WIDTH+context.getScrollX());
		int dayWidth = WeekViewActivity.DAY_WIDTH;
		p = new Paint();

		AcalDateTime currentDay = this.firstVisibleDay.clone().applyLocalTimeZone();
		currentDay.addDays(-1);
		//draw events
		while (dayX<= width) {
			
			if ( Constants.LOG_DEBUG && Constants.debugWeekView )
				Log.d(TAG,"Starting new day "+AcalDateTime.fmtDayMonthYear(currentDay)+
							" epoch="+currentDay.getEpoch()+" dayX="+dayX);
		
			SimpleAcalEvent[][] timeTable = getInDayTimeTable(currentDay);

			//draw visible events
			if ( timeTable.length > 0) {
				p.reset();
				p.setStyle(Paint.Style.FILL);
				long thisDayEpoch = currentDay.getEpoch();

				//events can show up several times in the timetable, keep a record of what's already drawn
				Set<SimpleAcalEvent> drawn = new HashSet<SimpleAcalEvent>();

				for (int i = 0; i < timeTable.length; i++)  {
					int curX = 0;
					for(int j=0; j < timeTable[i].length; j++) {
						if (timeTable[i][j] != null) {
							if (drawn.contains(timeTable[i][j])) {
								curX+=timeTable[i][j].getLastWidth();
								continue;
							}
							drawn.add(timeTable[i][j]);
							
							//calculate width
							int depth  = 0;
							for ( int k = j;
									k<=lastMaxX && (timeTable[i][k] == null || timeTable[i][k] == timeTable[i][j]);
									k++)
								depth++;
							float singleWidth = (dayWidth/(lastMaxX+1)) * depth;	
							SimpleAcalEvent event = timeTable[i][j];
							drawVertical(event, canvas, (int)dayX+curX, (int)singleWidth, thisDayEpoch);
							event.setLastWidth((int)singleWidth);
							curX+=singleWidth;
						}
					}
				}
			}
			currentDay.addDays(1);
			dayX+=dayWidth;
		}
		
		//border
		p.reset();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(0xff333333);
		canvas.drawRect(0, 0, width, PxH, p);

		//draw shading effect below header
		if (PxH != 0 ) {
			int hhh = 1200/WeekViewActivity.SECONDS_PER_PIXEL;
		 
			int base = 0x444444;
			int current = 0xc0;
			int decrement = current/hhh;
			hhh += PxH;
		
			int color = (current << 24)+base; 
			p.setColor(color);
			canvas.drawLine(0, PxH, width, PxH, p);

			for (int i=PxH; i<hhh; i++) {
				current-=decrement;
				color = (current << 24)+base; 
				p.setColor(color);
				canvas.drawLine(0, i, width, i, p);
			}
		}
		

	}
	
	private void drawBackground(Canvas canvas) {
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#AAf0f0f0"));
		canvas.drawRect(0, 0, width, TpX, p);
	}

	
	public void drawGrid(Canvas canvas, Paint p) {
		p.setStyle(Paint.Style.FILL);
		int dayX = (int)(0-WeekViewActivity.DAY_WIDTH+scrollx);
		AcalDateTime currentDay = context.getCurrentDate().clone();
		currentDay.addDays(-1);
		
		//get the grid for each day
		Bitmap dayGrid = context.getImageCache().getDayBox(TpX+(3600/WeekViewActivity.SECONDS_PER_PIXEL));
		int y = PxH;
		int offset = PxH + ((topSec%3600)/WeekViewActivity.SECONDS_PER_PIXEL);

		// The location of the work part of the day
		int workTop = (context.WORK_START_SECONDS - topSec) / WeekViewActivity.SECONDS_PER_PIXEL;
		int workBot = (context.WORK_FINISH_SECONDS- topSec) / WeekViewActivity.SECONDS_PER_PIXEL;
		if ( workTop < PxH ) workTop = PxH;
		
		//dayGrid = Bitmap.createBitmap(dayGrid, 0, offset, dayGrid.getWidth(), PxD);
		Rect src = new Rect(0,offset,dayGrid.getWidth(),offset+PxD);
		while ( dayX <= width) {
			if (!(currentDay.getWeekDay() == AcalDateTime.SATURDAY || currentDay.getWeekDay() == AcalDateTime.SUNDAY)) {
				//add a yellow background around to the work hours
				canvas.drawRect(dayX, workTop, dayX+WeekViewActivity.DAY_WIDTH, workBot, workPaint);
			}

			Rect dst = new Rect(dayX,y,dayX+dayGrid.getWidth(),y+PxD);
			canvas.drawBitmap(dayGrid, src, dst, p);
			dayX += WeekViewActivity.DAY_WIDTH;
			currentDay.addDays(1);
		}
		
	}

	

	/**
	 * Draw the event in the main part of the day.
	 *  
	 * @param event to be drawn
	 * @param canvas to draw on
	 * @param x position from left, in pixels
	 * @param width of the event to draw, in pixels
	 * @param dayStart in seconds from epoch
	 */
	public void drawVertical(SimpleAcalEvent event, Canvas canvas, int x,  int width, long dayStart) {

		long topStart = dayStart + topSec;
		if ( Constants.LOG_VERBOSE && Constants.debugWeekView ) {
			Log.v(TAG,"Drawing event "+event.getTimeText(context, dayStart, dayStart, true)+
						": '"+event.summary+"' at "+x+" for "+width+" ~ "+
						event.start+","+event.end+" ~ "+dayStart+", topSec: "+topSec);
			Log.v(TAG,"Top="+(event.start - topStart)+
						", Bottom="+(event.end - topStart) );
		}
		
		int maxWidth = width;
		if ( x < 0 ) {
			width = width + x;
			x = 0;
		}

		if ( width < 1f ) {
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Event is width "+ width);
			return;
		}
		
		int bottom = (int) ((event.end - topStart)/WeekViewActivity.SECONDS_PER_PIXEL);
		if ( bottom < PxH )  {  // Event is off top
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Event is off top by "+ bottom + " vs. " + PxH);
			return;
		}

		int top = (int) ((event.start - topStart)/WeekViewActivity.SECONDS_PER_PIXEL);
		if ( top > TpX ) { // Event is off bottom
			if ( Constants.LOG_VERBOSE ) Log.v(TAG,"Event is off bottom by "+ top + " vs. " + TpX);
			return;
		}

		int maxHeight = Math.max((bottom - top), WeekViewActivity.MINIMUM_DAY_EVENT_HEIGHT );
		int height = maxHeight;

		if ( top < PxH ) {
			height -= (PxH-top);
			top = PxH;
		}
		if ( bottom > TpX ) bottom = TpX;
		if ( height < 1 ) return;

		// Paint a gray border
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(0xff555555);

		if ( Constants.LOG_VERBOSE && Constants.debugWeekView )
			Log.v(TAG,"Drawing event "+event.getTimeText(context, dayStart, dayStart, true)+
						": '"+event.summary+"' at "+x+","+top+" for "+width+","+height+
						" ("+maxWidth+","+maxHeight+")"+
						" - "+event.start+","+event.end);

		canvas.drawBitmap(context.getImageCache().getEventBitmap(event.resourceId,event.summary,event.colour,
						width, height, maxWidth, maxHeight), x, top, new Paint());
		
	}

	public void drawHorizontal(SimpleAcalEvent event, Canvas c, int depth) {
		event.calulateMaxWidth(width, HSPP);
		int x = (int)(event.start-HST)/HSPP;
		if ( x < 0 ) x = 0;
		int y = HIH*depth;
		int maxWidth = event.getMaxWidth();
		int actualWidth = (int)Math.min(Math.min(event.getActualWidth(), width-x),(event.end-HST)/HSPP); 
		int height = HIH;
		if (height <= 0 || actualWidth<=0 || maxWidth<=0) return;
		c.drawBitmap(context.getImageCache().getEventBitmap(event.resourceId,event.summary,event.colour,
					actualWidth, height, maxWidth, height), x,y, new Paint());
	}
	
	/**
	 * Calculates a timetable of rows to place horizontal (multi-day) events in order
	 * that the events do not overlap one other.
	 * @param range A one-dimensional array of events
	 * @return A two-dimensional array of events
	 */
	private SimpleAcalEvent[][] getMultiDayTimeTable(AcalDateRange range) {
		List<SimpleAcalEvent> events = context.getEventsForDays(range, WeekViewActivity.INCLUDE_ALL_DAY_EVENTS );
		if (HTimetable != null && HSimpleList != null) {
			if (HSimpleList.containsAll(events) && events.size() == HSimpleList.size()) return HTimetable;
		}
		HSimpleList = events;
		Collections.sort(HSimpleList);
		SimpleAcalEvent[][] timetable = new SimpleAcalEvent[HSimpleList.size()][HSimpleList.size()]; //maximum possible
		int depth = 0;
		for (int x = 0; x < HSimpleList.size(); x++) {
			SimpleAcalEvent ev = HSimpleList.get(x);
			if ( ev.start >= this.HET || ev.end <= this.HST ) continue;  // Discard any events out of range.
			int i = 0;
			boolean go = true;
			while(go) {
				SimpleAcalEvent[] row = timetable[i];
				int j=0;
				while(true) {
					if (row[j] == null) { row[j] = ev; go=false; break; }
					else if (!(row[j].end > (ev.start))) {j++; continue; }
					else break;
				}
				i++;
			}
			depth = Math.max(depth,i);
		}
		HDepth = depth;
		HTimetable = timetable;
		return timetable;
	}
	
	private SimpleAcalEvent[][] getInDayTimeTable(AcalDateTime day) {
		List<SimpleAcalEvent> events = context.getEventsForDays(new AcalDateRange(day,day.clone().addDays(1)),
					WeekViewActivity.INCLUDE_IN_DAY_EVENTS );
		Collections.sort(events);
		SimpleAcalEvent[][] timetable = new SimpleAcalEvent[events.size()][events.size()]; //maximum possible
		int maxX = 0;
		for (SimpleAcalEvent seo : events){
			int x = 0; int y = 0;
			while (timetable[y][x] != null) {
				if (seo.overlaps(timetable[y][x])) {
					
					//if our end time is before [y][x]'s, we need to extend[y][x]'s range
					if (seo.end < (timetable[y][x].end)) timetable[y+1][x] = timetable[y][x];
					x++;
				} else {
					y++;
				}
			}
			timetable[y][x] = seo;
			if (x > maxX) maxX = x;
		}
		lastMaxX = maxX;
		return timetable;
	}

}
