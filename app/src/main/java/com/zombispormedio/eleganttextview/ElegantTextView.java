package com.zombispormedio.eleganttextview;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.BiFunction;
import com.annimon.stream.function.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;


/**
 * Created by xavierserrano on 23/11/16.
 */

public class ElegantTextView extends TextView implements StyleContext {



    private String mTemplate;

    private String startPoint="{";
    private String endPoint="}";

    private LinkedHashMap<String, StyleableValue> values;

    private LinkedHashMap<String, ElegantUtils.AbstractFilter> filters;

    private HashSet<String> globals;

    public ElegantTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        obtainAttributes(context, attrs);

        values = new LinkedHashMap<>();

        filters = new LinkedHashMap<>();

        globals=new HashSet<>();

        configureDefaultFilters();

    }

    private void configureDefaultFilters() {
        filter(ElegantUtils.Style.BOLD, ElegantUtils::bold);
        filter(ElegantUtils.Style.FOREGROUND_COLOR, ElegantUtils::foregroundColor);
        filter(ElegantUtils.Style.BACKGROUND_COLOR, ElegantUtils::backgroundColor);

        filter(ElegantUtils.Style.TEXT_CENTER, ElegantUtils::centerText);
    }

    private void obtainAttributes(Context context, AttributeSet attrs) {

        TypedArray styledAttributes = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ElegantTextView,
                0, 0);

        try {
            mTemplate = styledAttributes.getString(R.styleable.ElegantTextView_template);


        } finally {
            styledAttributes.recycle();
        }

    }

    public ElegantTextView template(String mTemplate) {
        this.mTemplate = mTemplate;
        return this;
    }


    public ElegantTextView bind(String key, CharSequence value) {
        if (values.containsKey(key)) {
            values.get(key).setValue(value);
        } else {
            values.put(key, new StyleableValue(value));
        }

        return this;
    }

    public ElegantTextView bind(String key, CharSequence v, String... styles) {
        StyleableValue value;

        if (values.containsKey(key)) {
            value = values.get(key);
            value.setValue(v);
        } else {
            value = new StyleableValue(v);
            values.put(key, value);
        }

        value.addStyle(styles);

        return this;

    }

    public ElegantTextView bindStyle(String key, String... styles) {
        StyleableValue value;

        if (values.containsKey(key)) {
            value = values.get(key);
        } else {
            value = new StyleableValue();
            values.put(key, value);
        }

        value.addStyle(styles);

        return this;
    }


    public ElegantTextView filter(String key, Function<SpannableString, SpannableString> function) {
        filters.put(key, new ElegantUtils.Filter(function));
        return this;
    }

    public ElegantTextView filter(String key, BiFunction<SpannableString, List<String>, SpannableString> function) {
        filters.put(key, new ElegantUtils.ArgsFilter(function));
        return this;
    }

    public ElegantTextView addGlobal(String... keys){
        Collections.addAll(globals, keys);
        return this;
    }

    public ElegantTextView removeBindingValue(String key){
        values.remove(key);
        return this;
    }

    public ElegantTextView removeFilter(String key){
        filters.remove(key);
        return this;
    }

    public ElegantTextView removeGlobal(String key){
        globals.remove(key);
        return this;
    }

    public ElegantTextView bindingPoints(String startPoint, String endPoint) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        return this;
    }


    public ElegantTextView apply() {
        if(mTemplate.isEmpty())return this;

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(mTemplate);

        builder = buildBinding(builder);

        builder=buildGlobal(builder);

        setText(builder, BufferType.SPANNABLE);
        return this;

    }

    private SpannableStringBuilder buildGlobal(SpannableStringBuilder builder) {
        SpannableStringBuilder newBuilder=new SpannableStringBuilder();

        CharSequence result=Stream.of(globals).reduce(builder, this::applyFilter);

        newBuilder.append(result);

        return newBuilder;
    }

    private SpannableStringBuilder buildBinding(SpannableStringBuilder builder) {
       return Stream.of(values).reduce(builder, (memo, entry) -> {

            String key = entry.getKey();

            CharSequence value = entry.getValue().applyFilters(this);

            String pattern = buildBindingPattern(key);

            int start = mTemplate.indexOf(pattern);

            if(start==-1)return memo;

            int end = start + pattern.length();

            return memo.replace(start, end, value);
        });
    }

    private String buildBindingPattern(String key) {
        return startPoint+key+endPoint;
    }


    @Override
    public SpannableString applyFilter(CharSequence value, String key) {
        String realKey=key;
        SpannableString result=new SpannableString(value);

        ArrayList<String> args=new ArrayList<>();
        if(key.contains(":")){
            String[] elems=key.split(":");
            realKey=elems[0];
            Collections.addAll(args, elems[1].split(","));
        }
        
        if(filters.containsKey(realKey)){
            ElegantUtils.AbstractFilter filter=filters.get(realKey);

            if(args.size()>0 && filter instanceof ElegantUtils.ArgsFilter){

                result=((ElegantUtils.ArgsFilter)filter)
                        .getFunction()
                        .apply(result, args);
            }else{
                if(filter instanceof ElegantUtils.Filter){
                    result=((ElegantUtils.Filter)filter).getFunction().apply(result);
                }
            }
        }

        return result;
    }
}
