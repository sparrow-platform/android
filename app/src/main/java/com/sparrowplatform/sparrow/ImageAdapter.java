package com.sparrowplatform.sparrow;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;


public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private ArrayList<Image> mDataset;
    private Records mActivity;



    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;
        public ImageView mImageView;
        public ImageView mLikeButton;

        public ViewHolder(View v) {
            super(v);
            mTextView = v.findViewById(R.id.textView2);
            mImageView = v.findViewById(R.id.imageView);
            mLikeButton = v.findViewById(R.id.moreButton);
        }


    }

    public ImageAdapter(ArrayList<Image> myDataset, Records activity) {
        mDataset = myDataset;
        mActivity = activity;
    }


    // Create new views (invoked by the layout manager)
    @Override
    public ImageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.image_view, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {

        final Image image = (Image) mDataset.get(position);
        if (image.user != null) {
            holder.mTextView.setText(image.user.displayName);
        }
        holder.mLikeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String content = image.content;
                Intent activityChangeIntent = new Intent(mActivity, FullscreenActivity.class);
                activityChangeIntent.putExtra("Image data", image);
                mActivity.startActivity(activityChangeIntent);

            }
        });


        Picasso.get().load(image.downloadUrl).into(holder.mImageView);

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public void addImage(Image image) {
        mDataset.add(0, image);
        notifyDataSetChanged();
    }
}
