/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package ost.com.demoapp.ui.dashboard;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import ost.com.demoapp.R;
import ost.com.demoapp.entity.Transaction;
import ost.com.demoapp.ui.dashboard.UserListFragment.OnListFragmentInteractionListener;

/**
 * {@link RecyclerView.Adapter} that can display a {@link ost.com.demoapp.entity.User} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
class TransactionRecyclerViewAdapter extends RecyclerView.Adapter<TransactionRecyclerViewAdapter.ViewHolder> {

    private final List<Transaction> mValues;
    private final OnListInteractionListener mListener;

    private TransactionRecyclerViewAdapter(List<Transaction> items, OnListInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    public static TransactionRecyclerViewAdapter newInstance(List<Transaction> items, OnListInteractionListener listener) {
        return new TransactionRecyclerViewAdapter(items, listener);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mTransaction = mValues.get(position);
        holder.mTransferType.setText(holder.mTransaction.getMetaName());
        holder.mDate.setText(new Date((long)holder.mTransaction.getTimestamp()).toString());
        holder.mTransferValue.setText(
                holder.mTransaction.isIn() ? holder.mTransaction.getValue(): "-" + holder.mTransaction.getValue()
        );

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListViewInteraction(holder.mTransaction);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mTransferType;
        public final TextView mDate;
        private final TextView mTransferValue;
        private final ImageView mImageView;
        public Transaction mTransaction;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mImageView = (ImageView) view.findViewById(R.id.iv_transfer_image);
            mTransferType = (TextView) view.findViewById(R.id.tv_transfer_type);
            mDate = (TextView) view.findViewById(R.id.tv_timestamp);
            mTransferValue = (TextView) view.findViewById(R.id.tv_value);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mDate.getText() + "'";
        }
    }

    public interface OnListInteractionListener {
        void onListViewInteraction(Transaction transaction);
    }
}