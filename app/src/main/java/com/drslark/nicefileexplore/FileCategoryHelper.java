
package com.drslark.nicefileexplore;

import java.io.FilenameFilter;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.drslark.nicefileexplore.model.FileStoreData;
import com.drslark.nicefileexplore.model.MediaStoreData;
import com.drslark.nicefileexplore.utils.FileSortHelper;
import com.drslark.nicefileexplore.utils.FilenameExtFilter;
import com.drslark.nicefileexplore.utils.Util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

public class FileCategoryHelper {

    public static final int All = 8000;
    public static final int Music = 8001;
    public static final int Video = 8002;
    public static final int Picture = 8003;
    public static final int Doc = 8004;
    public static final int Zip = 8005;
    public static final int Apk = 8006;
    public static final int Custom = 8007;
    public static final int BlueTooth = 8008;
    public static final int Favorite = 8009;
    public static final int Other = 8010;
    private static final String TAG = "intHelper";
    private static final int PAGE_SIZE = 50;
    private static final String[] FILE_PROJECTION = {
            FileColumns._ID, FileColumns.DATA, FileColumns.DATE_ADDED, FileColumns.DATE_MODIFIED, FileColumns.MIME_TYPE,
            FileColumns.SIZE
    };
    private static final String[] IMAGE_PROJECTION =
            new String[]{
                    MediaStore.Images.ImageColumns._ID,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Images.ImageColumns.DATE_TAKEN,
                    MediaStore.Images.ImageColumns.DATE_MODIFIED,
                    MediaStore.Images.ImageColumns.MIME_TYPE,
                    MediaStore.Images.ImageColumns.ORIENTATION,
            };
    private static final String[] VIDEO_PROJECTION =
            new String[]{
                    MediaStore.Video.VideoColumns._ID,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Video.VideoColumns.DATE_TAKEN,
                    MediaStore.Video.VideoColumns.DATE_MODIFIED,
                    MediaStore.Video.VideoColumns.MIME_TYPE,
                    "0 AS " + MediaStore.Images.ImageColumns.ORIENTATION,
            };
    public static SparseArray<FilenameExtFilter> filters
            = new SparseArray<>();
    public static SparseArray<Integer> categoryNames
            = new SparseArray<>();
    public static int[] sCategories = new int[]{
            Music, Video, Picture,
            Doc, Zip, Apk, BlueTooth
    };
    private static String APK_EXT = "apk";
    private static String[] ZIP_EXTS = new String[]{
            "zip", "rar"
    };

    static {
        categoryNames.put(All, R.string.category_all);
        categoryNames.put(Music, R.string.category_music);
        categoryNames.put(Video, R.string.category_video);
        categoryNames.put(Picture, R.string.category_pic);
        categoryNames.put(Doc, R.string.category_doc);
        categoryNames.put(Zip, R.string.category_zip);
        categoryNames.put(Apk, R.string.category_apk);
        categoryNames.put(BlueTooth, R.string.category_bluetooth);
        categoryNames.put(Favorite, R.string.category_fav);
    }

    private int mCategory;
    private Context mContext;
    private SparseArray<CategoryInfo> mCategoryInfo = new SparseArray<>();

    public FileCategoryHelper(Context context) {
        mContext = context;
        mCategory = All;
    }

    public static  <T extends List<Element>, Element> List<Element> mergeSort(T first, T second, Comparator<Element>
            comparator) {
        List<Element> result = new LinkedList<>();
        Log.d(TAG, "first Size:" + first.size());
        Log.d(TAG, "second Size:" + second.size());
        int i = 0;
        int j = 0;
        while (i < first.size() && j < second.size()) {
            if (comparator.compare(first.get(i), second.get(j)) < 0) {
                result.add(first.get(i));
                i++;
            } else {
                result.add(second.get(j));
                j++;
            }
        }
        if (i < first.size()) {
            result.addAll(first.subList(i, first.size()));
        }
        if (j < second.size()) {
            result.addAll(second.subList(j, second.size()));
        }
        return result;

    }

    public int getCurCategory() {
        return mCategory;
    }

    public void setCurCategory(int c) {
        mCategory = c;
    }

    public int getCurCategoryNameResId() {
        return categoryNames.get(mCategory);
    }

    public void setCustomCategory(String[] exts) {
        mCategory = Custom;
        if (filters.indexOfKey(Custom) > 0) {
            filters.remove(Custom);
        }

        filters.put(Custom, new FilenameExtFilter(exts));
    }

    public FilenameFilter getFilter() {
        return filters.get(mCategory);
    }

    public SparseArray<CategoryInfo> getCategoryInfos() {
        return mCategoryInfo;
    }

    public CategoryInfo getCategoryInfo(int fc) {
        if (mCategoryInfo.indexOfKey(fc) > 0) {
            return mCategoryInfo.get(fc);
        } else {
            CategoryInfo info = new CategoryInfo();
            mCategoryInfo.put(fc, info);
            return info;
        }
    }

    private void setCategoryInfo(int fc, long count, long size) {
        CategoryInfo info = mCategoryInfo.get(fc);
        if (info == null) {
            info = new CategoryInfo();
            mCategoryInfo.put(fc, info);
        }
        info.count = count;
        info.size = size;
    }

    private String buildDocSelection(String mimeType) {
        return "(" + FileColumns.MIME_TYPE + "=='" + mimeType + "')";
    }

    private String buildDocSelection() {
        StringBuilder selection = new StringBuilder();
        for (String aSDocMimeTypesSet : Util.sDocMimeTypesSet) {
            selection.append("(" + FileColumns.MIME_TYPE + "=='")
                    .append(aSDocMimeTypesSet).append("') OR ");
        }
        String result = selection.substring(0, selection.lastIndexOf(")") + 1);
        Log.d(TAG, "docSelection : " + result);
        return result;
    }

    private String buildSelectionByCategory(int cat) {
        String selection = null;
        switch (cat) {
            case Doc:
                selection = buildDocSelection();
                break;
            case Zip:
                selection = "(" + FileColumns.MIME_TYPE + " == '" + Util.sZipFileMimeType + "')";
                break;
            case Apk:
                selection = FileColumns.DATA + " LIKE '%.apk'";
                break;
            default:
                selection = null;
        }
        return selection;
    }

    private Uri getContentUriByCategory(int cat) {
        Uri uri;
        String volumeName = "external";
        switch (cat) {
            case Doc:
            case Zip:
            case Apk:
                uri = Files.getContentUri(volumeName);
                break;
            case Music:
                uri = Audio.Media.getContentUri(volumeName);
                break;
            case Video:
                uri = MediaStore.Video.Media.getContentUri(volumeName);
                break;
            case Picture:
                uri = Images.Media.getContentUri(volumeName);
                break;
            default:
                uri = null;
        }
        return uri;
    }

    private String buildSortOrder(int sort) {
        String sortOrder = null;
        switch (sort) {
            case FileSortHelper.NAME:
                sortOrder = FileColumns.TITLE + " asc";
                break;
            case FileSortHelper.SIZE:
                sortOrder = FileColumns.SIZE + " asc";
                break;
            case FileSortHelper.DATE:
                sortOrder = FileColumns.DATE_MODIFIED + " desc";
                break;
            case FileSortHelper.TYPE:
                sortOrder = FileColumns.MIME_TYPE + " asc, " + FileColumns.TITLE + " asc";
                break;
        }
        return sortOrder;
    }

    public List<FileStoreData> query(int fc, int sort, @Nullable String[] columns) {

        Uri uri = getContentUriByCategory(fc);
        String selection = buildSelectionByCategory(fc);
        String sortOrder = buildSortOrder(sort);
        if (uri == null) {
            return null;
        }
        if (columns == null) {
            columns = FILE_PROJECTION;
        }
        Cursor cursor = mContext.getContentResolver().query(uri, columns, selection, null, sortOrder);
        if (cursor == null) {
            return null;
        }
        LinkedList<FileStoreData> files = new LinkedList<>();
        while (cursor.moveToNext()) {
            if (cursor.getLong(5) == 0) {
                continue;
            }
            Long id = cursor.getLong(0);
            FileStoreData data = new FileStoreData(Uri.withAppendedPath(getContentUriByCategory(fc),
                    id.toString()), id,
                    cursor.getString(1), cursor.getLong(2), cursor.getLong(3),
                    cursor.getString(4), cursor.getLong(5));
            files.add(data);
        }
        Log.d(TAG, "endCursor" + System.currentTimeMillis());
        return files;
    }

    public void refreshCategoryInfo() {
        // clear
        for (int fc : sCategories) {
            setCategoryInfo(fc, 0, 0);
        }

        // query database
        String volumeName = "external";

        Uri uri = Audio.Media.getContentUri(volumeName);
        refreshMediaCategory(Music, uri);

        uri = MediaStore.Video.Media.getContentUri(volumeName);
        refreshMediaCategory(Video, uri);

        uri = Images.Media.getContentUri(volumeName);
        refreshMediaCategory(Picture, uri);

        uri = Files.getContentUri(volumeName);
        refreshMediaCategory(Doc, uri);
        refreshMediaCategory(Zip, uri);
        refreshMediaCategory(Apk, uri);
    }

    private boolean refreshMediaCategory(int fc, Uri uri) {
        String[] columns = new String[]{
                "COUNT(*)", "SUM(_size)"
        };
        Cursor c = mContext.getContentResolver().query(uri, columns, buildSelectionByCategory(fc), null, null);
        if (c == null) {
            Log.e(TAG, "fail to query uri:" + uri);
            return false;
        }

        if (c.moveToNext()) {
            setCategoryInfo(fc, c.getLong(0), c.getLong(1));
            c.close();
            return true;
        }

        return false;
    }

    public List<MediaStoreData> queryImages() {
        return mergeSort(
                query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION,
                        MediaStore.Images.ImageColumns.DATE_TAKEN,
                        MediaStoreData.Type.IMAGE),
                query(Images.Media.INTERNAL_CONTENT_URI, IMAGE_PROJECTION,
                        MediaStore.Images.ImageColumns.DATE_TAKEN,
                        MediaStoreData.Type.IMAGE),
                new Comparator<MediaStoreData>() {
                    @Override
                    public int compare(MediaStoreData lhs, MediaStoreData rhs) {
                        return lhs.dateModified >= rhs.dateModified ? 1 : -1;
                    }
                });
    }

    public List<MediaStoreData> queryVideos() {
        return mergeSort(
                query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_PROJECTION,
                        MediaStore.Video.VideoColumns.DATE_TAKEN,
                        MediaStoreData.Type.VIDEO),
                query(MediaStore.Video.Media.INTERNAL_CONTENT_URI, VIDEO_PROJECTION,
                        MediaStore.Video.VideoColumns.DATE_TAKEN,
                        MediaStoreData.Type.VIDEO),
                new Comparator<MediaStoreData>() {
                    @Override
                    public int compare(MediaStoreData lhs, MediaStoreData rhs) {
                        return lhs.dateModified >= rhs.dateModified ? 1 : -1;
                    }
                });
    }

    private List<MediaStoreData> query(Uri contentUri, String[] projection, String sortByCol, MediaStoreData.Type type) {
        final List<MediaStoreData> data = new LinkedList<>();
        Cursor cursor = mContext.getContentResolver()
                .query(contentUri, projection, null, null, sortByCol + " DESC");

        if (cursor == null) {
            return data;
        }
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                long dateTaken = cursor.getLong(2);
                long dateModified = cursor.getLong(3);
                String mimeType = cursor.getString(4);
                int orientation = cursor.getInt(5);

                data.add(new MediaStoreData(id, name, Uri.withAppendedPath(contentUri, Long.toString(id)),
                        mimeType, dateTaken, dateModified, orientation, type
                ));
            }
        } finally {
            cursor.close();
        }

        return data;
    }

    public class CategoryInfo {
        public long count;

        public long size;
    }


//    public static int getCategoryFromPath(String path) {
//        MediaFileType type = MediaFile.getFileType(path);
//        if (type != null) {
//            if (MediaFile.isAudioFileType(type.fileType)) return  Music;
//            if (MediaFile.isVideoFileType(type.fileType)) return  Video;
//            if (MediaFile.isImageFileType(type.fileType)) return  Picture;
//            if (Util.sDocMimeTypesSet.contains(type.mimeType)) return  Doc;
//        }
//
//        int dotPosition = path.lastIndexOf('.');
//        if (dotPosition < 0) {
//            return  Other;
//        }
//
//        String ext = path.substring(dotPosition + 1);
//        if (ext.equalsIgnoreCase(APK_EXT)) {
//            return  Apk;
//        }
//
//        if (matchExts(ext, ZIP_EXTS)) {
//            return  Zip;
//        }
//
//        return  Other;
//    }
//
//    private static boolean matchExts(String ext, String[] exts) {
//        for (String ex : exts) {
//            if (ex.equalsIgnoreCase(ext))
//                return true;
//        }
//        return false;
//    }
}
