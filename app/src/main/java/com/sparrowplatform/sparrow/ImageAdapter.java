package com.sparrowplatform.sparrow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import java.util.ArrayList;



public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>  {
    private ArrayList<Image> mDataset;
    private Records mActivity;


    private Target target;


    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;
        public TextView descriptionText;
        public ImageView mImageView;
        public LinearLayout card;
        public TextView fileNameTV;
        public TextView descriptionTV;

        public ViewHolder(View v) {
            super(v);
            mTextView = v.findViewById(R.id.textView2);
            descriptionText = v.findViewById(R.id.textView3);
            mImageView = v.findViewById(R.id.imageView);
            card = v.findViewById(R.id.card);
            fileNameTV = v.findViewById(R.id.textView4);

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

        holder.mTextView.setText(image.title);
        holder.descriptionText.setText(image.description);
        holder.fileNameTV.setText(image.localPath);


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

        holder.card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                holder.card.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {

//                        String imagePath = "/data/data/com.sparrowplatform.sparrow/files/emr/" + image.localPath;
//
//                        File newFile = new File(imagePath);
//                        MimeTypeMap mime = MimeTypeMap.getSingleton();
//                        String ext = newFile.getName().substring(newFile.getName().lastIndexOf(".") + 1);
//                        String type = mime.getMimeTypeFromExtension(ext);
//                        try {
//                            Intent intent = new Intent();
//                            intent.setAction(Intent.ACTION_VIEW);
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//                                Uri contentUri = FileProvider.getUriForFile(getApplicationContext(), "com.sparrowplatform.sparrow", newFile);
//                                intent.setDataAndType(contentUri, type);
//                            } else {
//                                intent.setDataAndType(Uri.fromFile(newFile), type);
//                            }
//                            getApplicationContext().startActivity(intent);
//                        } catch (ActivityNotFoundException anfe) {
//                            Toast.makeText(getApplicationContext(), "No activity found to open this attachment.", Toast.LENGTH_LONG).show();
//                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(mActivity, R.style.myDialog));

                        LayoutInflater inflater = (LayoutInflater)mActivity.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                        View dialogView = inflater.from(mActivity).inflate(R.layout.emr_document_view,null);
                        builder.setView(dialogView);


                        TextView title = (TextView) dialogView.findViewById(R.id.heading);
                        title.setText(image.title);

                        TextView descrption = (TextView) dialogView.findViewById(R.id.description);
                        descrption.setText(image.description);


                        final AlertDialog dialog = builder.create();
                        dialog.show();



                    }
                });

            }
        });

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

    private String fileExt(String url) {
        if (url.indexOf("?") > -1) {
            url = url.substring(0, url.indexOf("?"));
        }
        if (url.lastIndexOf(".") == -1) {
            return null;
        } else {
            String ext = url.substring(url.lastIndexOf(".") + 1);
            if (ext.indexOf("%") > -1) {
                ext = ext.substring(0, ext.indexOf("%"));
            }
            if (ext.indexOf("/") > -1) {
                ext = ext.substring(0, ext.indexOf("/"));
            }
            return ext.toLowerCase();

        }
    }
}
