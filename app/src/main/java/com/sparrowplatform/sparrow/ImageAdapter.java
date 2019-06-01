package com.sparrowplatform.sparrow;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static com.firebase.ui.auth.AuthUI.getApplicationContext;


public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private ArrayList<Image> mDataset;
    private Records mActivity;


    private Target target;


    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;
        public TextView descriptionText;
        public ImageView mImageView;

        public ViewHolder(View v) {
            super(v);
            mTextView = v.findViewById(R.id.textView2);
            descriptionText = v.findViewById(R.id.textView3);
            mImageView = v.findViewById(R.id.imageView);
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
    public void onBindViewHolder(final ViewHolder holder, final int position) {

        final Image image = (Image) mDataset.get(position);
//        if (image.user != null) {
//            holder.mTextView.setText(image.user.displayName);
//        }
        holder.mTextView.setText(image.title);
        holder.descriptionText.setText(image.description);

        final Context context = mActivity.getApplicationContext();

        target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                // Bitmap is loaded, use image here
                holder.mImageView.setImageBitmap(bitmap);
                holder.mImageView.setPadding(30,30,30,30);
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {

            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {

            }

        };


        Picasso.get().load(image.downloadUrl).into(target);

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
