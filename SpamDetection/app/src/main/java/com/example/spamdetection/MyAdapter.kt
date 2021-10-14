package com.example.spamdetection

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.util.*


class MyAdapter(context: Context?, data: ArrayList<SampleData>) :
    BaseAdapter() {
    var mContext: Context? = null
    var mLayoutInflater: LayoutInflater? = null
    var sample: ArrayList<SampleData>
    override fun getCount(): Int {
        return sample.size
    }

    override fun getItem(i: Int): Any {
        return sample[i]
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val mView = mLayoutInflater!!.inflate(R.layout.listview, null)
        val record_name = mView.findViewById<TextView>(R.id.record_name)
        val spam_prob = mView.findViewById<TextView>(R.id.spam_prob)
        record_name.setText(sample[i].record_name)
        spam_prob.setText(sample[i].spam_prob)
        return mView
    }

    init {
        mContext = context
        sample = data
        mLayoutInflater = LayoutInflater.from(mContext)
    }
}
