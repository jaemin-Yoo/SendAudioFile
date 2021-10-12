package com.example.recordingsdk30;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class MyAdapter extends BaseAdapter {
    Context mContext = null;
    LayoutInflater mLayoutInflater = null;
    ArrayList<SampleData> sample;

    public MyAdapter(Context context, ArrayList<SampleData> data){
        mContext = context;
        sample = data;
        mLayoutInflater = LayoutInflater.from(mContext);
    }


    @Override
    public int getCount() {
        return sample.size();
    }

    @Override
    public Object getItem(int i) {
        return sample.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View mView = mLayoutInflater.inflate(R.layout.listview, null);

        TextView record_name = mView.findViewById(R.id.record_name);
        TextView spam_prob = mView.findViewById(R.id.spam_prob);

        record_name.setText(sample.get(i).getRecord_name());
        spam_prob.setText(sample.get(i).getSpam_prob());

        return mView;
    }
}
