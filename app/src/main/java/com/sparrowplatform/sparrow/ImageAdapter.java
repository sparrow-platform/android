package com.sparrowplatform.sparrow;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;


public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private ArrayList<Image> mDataset;
    private Records mActivity;

    public static void createCachedFile(Context context, String key, ArrayList<Image> mDataset) throws
            IOException {

        for (Image file : mDataset) {
            FileOutputStream fos = context.openFileOutput (key, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream (fos);
            oos.writeObject (file);
            oos.close ();
            fos.close ();
        }
    }

    public static Object readCachedFile (Context context, String key) throws IOException, ClassNotFoundException {
        FileInputStream fis = context.openFileInput (key);
        ObjectInputStream ois = new ObjectInputStream (fis);
        Object object = ois.readObject ();
        return object;
    }




    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;
//        public ImageView mImageView;

        public ViewHolder(View v) {
            super(v);
            mTextView = v.findViewById(R.id.textView2);
//            mImageView = v.findViewById(R.id.imageView);
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
//        if (image.user != null) {
//            holder.mTextView.setText(image.user.displayName);
//        }
        holder.mTextView.setText(image.title);

//        holder.mImageView.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                String content = image.content;
//
//
//
//            }
//        });

        //First check if file available locally..
        //Else load using Picasso
//        Picasso.get().load(image.downloadUrl).into(holder.mImageView);

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
