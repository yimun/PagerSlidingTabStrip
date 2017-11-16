/*
 * Copyright (C) 2013 Andreas Stuetz <andreas.stuetz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.astuetz;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IdRes;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.astuetz.pagerslidingtabstrip.R;

import java.util.Locale;

@ViewPager.DecorView
public class PagerSlidingTabStrip extends HorizontalScrollView {

	public interface IconTabProvider {
		int getPageIconResId(int position);
	}

	public interface ViewTabProvider {
		View getPageView(int position);
	}

	public interface DrawableTabProvider {
		Drawable getPageDrawable(int position);
	}

	public interface OnTabLongClickListener {
		boolean onTabLongClick(View view, int position);
	}

	public interface OnTabClickListener {
		void onTabClick(View view, int position);
	}

	public interface OnTabStateChangeListener {
		void onTabStateChange(View view, int position, boolean isSelected);
	}

	// @formatter:off
	private static final int[] ATTRS = new int[] {
			android.R.attr.textSize,
			android.R.attr.textColor
	};
	// @formatter:on

	private LinearLayout.LayoutParams defaultTabLayoutParams;
	private LinearLayout.LayoutParams expandedTabLayoutParams;

	private PageListener pagerListener = null;
	private PagerAdapterObserver pagerAdapterObserver = null;
	private PagerSlidingTabStrip.OnTabClickListener onTabClickListener;
	private PagerSlidingTabStrip.OnTabLongClickListener onTabLongClickListener;
	private PagerSlidingTabStrip.OnTabStateChangeListener onTabStateChangeListener;

	private LinearLayout tabsContainer;
	private ViewPager pager;

	private int tabCount;

	private int currentPosition = 0;
	private float currentPositionOffset = 0f;

	private Paint rectPaint;
	private Paint dividerPaint;

	private int indicatorColor = 0xFF666666;
	private int underlineColor = 0x1A000000;
	private int dividerColor = 0x1A000000;

	private boolean shouldExpand = false;
	private boolean textAllCaps = true;

	private int scrollOffset = 52;
	private int indicatorHeight = 8;
	private int underlineHeight = 2;
	private int dividerPadding = 12;
	private int tabPadding = 24;
	private int dividerWidth = 1;

	private int tabTextSize = 12;
	private int tabTextColor = 0xFF666666;
	private Typeface tabTypeface = null;
	private int tabTypefaceStyle = Typeface.BOLD;

	private int lastScrollX = 0;

	private int tabBackgroundResId = 0;

	private int fixedIndicatorWidth = 0;
	private boolean roundIndicator = false;
	private @IdRes
	int customIndicatorId = 0;
	private Bitmap customIndicator;
	private int indicatorOffset = 0;
	private int indicatorPaddingBottom = 0;

	private Locale locale;

	public PagerSlidingTabStrip(Context context) {
		this(context, null);
	}

	public PagerSlidingTabStrip(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public PagerSlidingTabStrip(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		setFillViewport(true);
		setWillNotDraw(false);
		setOverScrollMode(OVER_SCROLL_NEVER);

		tabsContainer = new LinearLayout(context);
		tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
		tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		addView(tabsContainer);

		DisplayMetrics dm = getResources().getDisplayMetrics();

		scrollOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollOffset, dm);
		indicatorHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, indicatorHeight, dm);
		underlineHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, underlineHeight, dm);
		dividerPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerPadding, dm);
		tabPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, tabPadding, dm);
		dividerWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerWidth, dm);
		tabTextSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tabTextSize, dm);

		// get system attrs (android:textSize and android:textColor)

		TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);

		tabTextSize = a.getDimensionPixelSize(0, tabTextSize);
		tabTextColor = a.getColor(1, tabTextColor);

		a.recycle();

		// get custom attrs

		a = context.obtainStyledAttributes(attrs, R.styleable.PagerSlidingTabStrip);

		indicatorColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsIndicatorColor, indicatorColor);
		underlineColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsUnderlineColor, underlineColor);
		dividerColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsDividerColor, dividerColor);
		indicatorHeight = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsIndicatorHeight, indicatorHeight);
		underlineHeight = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsUnderlineHeight, underlineHeight);
		dividerPadding = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsDividerPadding, dividerPadding);
		tabPadding = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsTabPaddingLeftRight, tabPadding);
		tabBackgroundResId = a.getResourceId(R.styleable.PagerSlidingTabStrip_pstsTabBackground, tabBackgroundResId);
		shouldExpand = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsShouldExpand, shouldExpand);
		scrollOffset = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsScrollOffset, scrollOffset);
		textAllCaps = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsTextAllCaps, textAllCaps);
		fixedIndicatorWidth = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsFixedIndicatorWidth, fixedIndicatorWidth);
		roundIndicator = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsRoundIndicator, roundIndicator);
		customIndicatorId = a.getResourceId(R.styleable.PagerSlidingTabStrip_pstsCustomIndicator, customIndicatorId);
		if (customIndicatorId != 0) {
			customIndicator = BitmapFactory.decodeResource(getResources(), customIndicatorId);
		}
		indicatorOffset = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsIndicatorOffset, indicatorOffset);
		indicatorPaddingBottom = a.getDimensionPixelOffset(R.styleable.PagerSlidingTabStrip_pstsIndicatorPaddingBottom, indicatorPaddingBottom);

		a.recycle();

		rectPaint = new Paint();
		rectPaint.setAntiAlias(true);
		rectPaint.setStyle(Style.FILL);

		dividerPaint = new Paint();
		dividerPaint.setAntiAlias(true);
		dividerPaint.setStrokeWidth(dividerWidth);

		defaultTabLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		expandedTabLayoutParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

		if (locale == null) {
			locale = getResources().getConfiguration().locale;
		}
	}

	public void setupWithViewPager(ViewPager pager) {
		this.pager = pager;

		if (pager.getAdapter() == null) {
			throw new IllegalStateException("ViewPager does not have adapter instance.");
		}

		if (pagerListener == null) {
			pagerListener = new PageListener();
		} else {
			pager.removeOnPageChangeListener(pagerListener);
		}
		pager.addOnPageChangeListener(pagerListener);

		if (pagerAdapterObserver == null) {
			pagerAdapterObserver = new PagerAdapterObserver();
		} else {
			try {
				pager.getAdapter().unregisterDataSetObserver(pagerAdapterObserver);
			} catch (IllegalStateException e) {
			}
		}
		pager.getAdapter().registerDataSetObserver(pagerAdapterObserver);

		notifyDataSetChanged();
	}

	public void setOnTabClickListener(PagerSlidingTabStrip.OnTabClickListener listener) {
		this.onTabClickListener = listener;
	}

	public void setOnTabLongClickListener(PagerSlidingTabStrip.OnTabLongClickListener listener) {
		this.onTabLongClickListener = listener;
	}

	public void setOnTabStateChangeListener(PagerSlidingTabStrip.OnTabStateChangeListener listener) {
		this.onTabStateChangeListener = listener;
	}

	public void notifyDataSetChanged() {

		tabsContainer.removeAllViews();

		tabCount = pager.getAdapter().getCount();

		for (int i = 0; i < tabCount; i++) {

			if(this.pager.getAdapter() instanceof PagerSlidingTabStrip.IconTabProvider) {
				this.addIconTab(i, ((PagerSlidingTabStrip.IconTabProvider)this.pager.getAdapter()).getPageIconResId(i));
			} else if(this.pager.getAdapter() instanceof PagerSlidingTabStrip.DrawableTabProvider) {
				this.addIconTab(i, ((PagerSlidingTabStrip.DrawableTabProvider)this.pager.getAdapter()).getPageDrawable(i));
			} else if(this.pager.getAdapter() instanceof PagerSlidingTabStrip.ViewTabProvider) {
				this.addTab(i, ((PagerSlidingTabStrip.ViewTabProvider)this.pager.getAdapter()).getPageView(i));
			} else {
				this.addTextTab(i, this.pager.getAdapter().getPageTitle(i).toString());
			}

		}

		updateTabStyles();

		getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

			@SuppressWarnings("deprecation")
			@SuppressLint("NewApi")
			@Override
			public void onGlobalLayout() {

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
					getViewTreeObserver().removeGlobalOnLayoutListener(this);
				} else {
					getViewTreeObserver().removeOnGlobalLayoutListener(this);
				}
				currentPosition = pager.getCurrentItem();
				scrollToChild(currentPosition, 0);
				notifyTabStateChanged(currentPosition);
			}
		});
	}

	private void addTextTab(final int position, String title) {

		TextView tab = new TextView(getContext());
		tab.setText(title);
		tab.setGravity(Gravity.CENTER);
		tab.setSingleLine();

		addTab(position, tab);
	}

	private void addIconTab(final int position, int resId) {

		ImageButton tab = new ImageButton(getContext());
		tab.setImageResource(resId);

		addTab(position, tab);

	}

	private void addIconTab(int position, Drawable drawable) {
		ImageButton tab = new ImageButton(this.getContext());
		tab.setImageDrawable(drawable);
		this.addTab(position, tab);
	}

	private void addTab(final int position, View tab) {
		tab.setFocusable(true);
		tab.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(PagerSlidingTabStrip.this.onTabClickListener != null) {
					PagerSlidingTabStrip.this.onTabClickListener.onTabClick(v, position);
				}
				pager.setCurrentItem(position);
			}
		});

		tab.setPadding(tabPadding, 0, tabPadding, 0);
		tab.setOnLongClickListener(new OnLongClickListener() {
			public boolean onLongClick(View v) {
				return PagerSlidingTabStrip.this.onTabLongClickListener != null && PagerSlidingTabStrip.this.onTabLongClickListener.onTabLongClick(v, position);
			}
		});
		tabsContainer.addView(tab, position, shouldExpand ? expandedTabLayoutParams : defaultTabLayoutParams);
	}

	public View getTabView(int position) {
		return this.tabsContainer.getChildAt(position);
	}

	private void updateTabStyles() {

		for (int i = 0; i < tabCount; i++) {

			View v = tabsContainer.getChildAt(i);
			if (tabBackgroundResId > 0) {
				// FIXME 4.1手机上setBackGround后有可能导致padding丢失
				v.setBackgroundResource(tabBackgroundResId);
			}

			if (!(pager.getAdapter() instanceof ViewTabProvider) && v instanceof TextView) {

				TextView tab = (TextView) v;
				tab.setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize);
				tab.setTypeface(tabTypeface, tabTypefaceStyle);
				tab.setTextColor(tabTextColor);

				// setAllCaps() is only available from API 14, so the upper case is made manually if we are on a
				// pre-ICS-build
				if (textAllCaps) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
						tab.setAllCaps(true);
					} else {
						tab.setText(tab.getText().toString().toUpperCase(locale));
					}
				}
			}
		}

	}

	private void notifyTabStateChanged(int selectPosition) {
		for (int i = 0; i < tabCount; i++) {
			View v = tabsContainer.getChildAt(i);
			if (onTabStateChangeListener != null) {
				onTabStateChangeListener.onTabStateChange(v, i, i == selectPosition);
			}
		}
	}

	private void scrollToChild(int position, int offset) {

		if (tabCount == 0) {
			return;
		}

		int newScrollX = tabsContainer.getChildAt(position).getLeft() + offset;

		if (position > 0 || offset > 0) {
			newScrollX -= scrollOffset;
		}

		if (newScrollX != lastScrollX) {
			lastScrollX = newScrollX;
			scrollTo(newScrollX, 0);
		}

	}

	protected int getIndicatorLeft(View tabView) {
		int left;
		if (customIndicator != null) {
			left = getPaddingLeft() + tabView.getLeft() + (tabView.getWidth() - customIndicator.getWidth()) / 2;
		} else if (fixedIndicatorWidth > 0) {
			left = getPaddingLeft() + tabView.getLeft() + (tabView.getWidth() - fixedIndicatorWidth) / 2;
		} else {
			left = getPaddingLeft() + tabView.getLeft();
		}
		left += indicatorOffset;
		return left;
	}

	protected int getIndicatorRight(View tabView) {
		int right;
		if (customIndicator != null) {
			right = tabView.getRight() + getPaddingLeft() - (tabView.getWidth() - customIndicator.getWidth()) / 2;
		} else if (fixedIndicatorWidth > 0) {
			right = tabView.getRight() + getPaddingLeft() - (tabView.getWidth() - fixedIndicatorWidth) / 2;
		} else {
			right = tabView.getRight() + getPaddingLeft();
		}
		right += indicatorOffset;
		return right;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (isInEditMode() || tabCount == 0) {
			return;
		}
		drawIndicator(canvas);

		drawUnderline(canvas);

		drawDivider(canvas);
	}

	protected void drawIndicator(Canvas canvas) {
		final int height = getHeight();

		// draw indicator line

		rectPaint.setColor(indicatorColor);

		// default: line below current tab
		View currentTab = tabsContainer.getChildAt(currentPosition);
		float lineLeft = getIndicatorLeft(currentTab);
		float lineRight = getIndicatorRight(currentTab);

		// if there is an offset, start interpolating left and right coordinates between current and next tab
		if (currentPositionOffset > 0f && currentPosition < tabCount - 1) {

			View nextTab = tabsContainer.getChildAt(currentPosition + 1);
			final float nextTabLeft = getIndicatorLeft(nextTab);
			final float nextTabRight = getIndicatorRight(nextTab);

			lineLeft = (currentPositionOffset * nextTabLeft + (1f - currentPositionOffset) * lineLeft);
			lineRight = (currentPositionOffset * nextTabRight + (1f - currentPositionOffset) * lineRight);
		}

		if (customIndicator != null) {
			canvas.drawBitmap(customIndicator, lineLeft, height - customIndicator.getHeight() - indicatorPaddingBottom, rectPaint);
		} else if (roundIndicator) {
			RectF rectF = new RectF(lineLeft, height - indicatorHeight - indicatorPaddingBottom, lineRight, height - indicatorPaddingBottom);
			canvas.drawRoundRect(rectF, indicatorHeight / 2, indicatorHeight / 2, rectPaint);
		} else {
			canvas.drawRect(lineLeft, height - indicatorHeight - indicatorPaddingBottom, lineRight, height - indicatorPaddingBottom, rectPaint);
		}
	}

	protected void drawUnderline(Canvas canvas) {
		rectPaint.setColor(underlineColor);
		canvas.drawRect(0, getHeight() - underlineHeight, tabsContainer.getWidth(), getHeight(), rectPaint);
	}

	protected void drawDivider(Canvas canvas) {
		dividerPaint.setColor(dividerColor);
		for (int i = 0; i < tabCount - 1; i++) {
			View tab = tabsContainer.getChildAt(i);
			canvas.drawLine(tab.getRight(), dividerPadding, tab.getRight(), getHeight() - dividerPadding, dividerPaint);
		}
	}

	private class PageListener implements OnPageChangeListener {

		@Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			currentPosition = position;
			currentPositionOffset = positionOffset;
			if (tabsContainer.getChildAt(position) != null) {
				scrollToChild(position, (int) (positionOffset * tabsContainer.getChildAt(position).getWidth()));
			}

			invalidate();

		}

		@Override
		public void onPageScrollStateChanged(int state) {
			if (state == ViewPager.SCROLL_STATE_IDLE) {
				scrollToChild(pager.getCurrentItem(), 0);
			}

		}

		@Override
		public void onPageSelected(int position) {
			notifyTabStateChanged(position);
		}

	}

	private class PagerAdapterObserver extends DataSetObserver {
		@Override
		public void onChanged() {
			notifyDataSetChanged();
		}

		@Override
		public void onInvalidated() {
			notifyDataSetChanged();
		}
	}

	public void setIndicatorColor(int indicatorColor) {
		this.indicatorColor = indicatorColor;
		invalidate();
	}

	public void setIndicatorColorResource(int resId) {
		this.indicatorColor = getResources().getColor(resId);
		invalidate();
	}

	public int getIndicatorColor() {
		return this.indicatorColor;
	}

	public void setIndicatorHeight(int indicatorLineHeightPx) {
		this.indicatorHeight = indicatorLineHeightPx;
		invalidate();
	}

	public int getIndicatorHeight() {
		return indicatorHeight;
	}

	public void setUnderlineColor(int underlineColor) {
		this.underlineColor = underlineColor;
		invalidate();
	}

	public void setUnderlineColorResource(int resId) {
		this.underlineColor = getResources().getColor(resId);
		invalidate();
	}

	public int getUnderlineColor() {
		return underlineColor;
	}

	public void setDividerColor(int dividerColor) {
		this.dividerColor = dividerColor;
		invalidate();
	}

	public void setDividerColorResource(int resId) {
		this.dividerColor = getResources().getColor(resId);
		invalidate();
	}

	public int getDividerColor() {
		return dividerColor;
	}

	public void setUnderlineHeight(int underlineHeightPx) {
		this.underlineHeight = underlineHeightPx;
		invalidate();
	}

	public int getUnderlineHeight() {
		return underlineHeight;
	}

	public void setDividerPadding(int dividerPaddingPx) {
		this.dividerPadding = dividerPaddingPx;
		invalidate();
	}

	public int getDividerPadding() {
		return dividerPadding;
	}

	public void setScrollOffset(int scrollOffsetPx) {
		this.scrollOffset = scrollOffsetPx;
		invalidate();
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public void setShouldExpand(boolean shouldExpand) {
		this.shouldExpand = shouldExpand;
		requestLayout();
	}

	public boolean getShouldExpand() {
		return shouldExpand;
	}

	public boolean isTextAllCaps() {
		return textAllCaps;
	}

	public void setAllCaps(boolean textAllCaps) {
		this.textAllCaps = textAllCaps;
	}

	public void setTextSize(int textSizePx) {
		this.tabTextSize = textSizePx;
		updateTabStyles();
	}

	public int getTextSize() {
		return tabTextSize;
	}

	public void setTextColor(int textColor) {
		this.tabTextColor = textColor;
		updateTabStyles();
	}

	public void setTextColorResource(int resId) {
		this.tabTextColor = getResources().getColor(resId);
		updateTabStyles();
	}

	public int getTextColor() {
		return tabTextColor;
	}

	public void setTypeface(Typeface typeface, int style) {
		this.tabTypeface = typeface;
		this.tabTypefaceStyle = style;
		updateTabStyles();
	}

	public void setTabBackground(int resId) {
		this.tabBackgroundResId = resId;
	}

	public int getTabBackground() {
		return tabBackgroundResId;
	}

	public void setTabPaddingLeftRight(int paddingPx) {
		this.tabPadding = paddingPx;
		updateTabStyles();
	}

	public int getTabPaddingLeftRight() {
		return tabPadding;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;
		super.onRestoreInstanceState(savedState.getSuperState());
		currentPosition = savedState.currentPosition;
		requestLayout();
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		SavedState savedState = new SavedState(superState);
		savedState.currentPosition = currentPosition;
		return savedState;
	}

	static class SavedState extends BaseSavedState {
		int currentPosition;

		public SavedState(Parcelable superState) {
			super(superState);
		}

		private SavedState(Parcel in) {
			super(in);
			currentPosition = in.readInt();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(currentPosition);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

}