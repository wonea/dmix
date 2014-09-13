/*
 * Copyright (C) 2010-2014 The MPDroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.namelessdev.mpdroid.fragments;

import com.namelessdev.mpdroid.MPDApplication;
import com.namelessdev.mpdroid.R;
import com.namelessdev.mpdroid.adapters.ArrayAdapter;
import com.namelessdev.mpdroid.helpers.AlbumCoverDownloadListener;
import com.namelessdev.mpdroid.helpers.CoverAsyncHelper;
import com.namelessdev.mpdroid.helpers.CoverInfo;
import com.namelessdev.mpdroid.helpers.CoverManager;
import com.namelessdev.mpdroid.tools.Tools;
import com.namelessdev.mpdroid.views.SongDataBinder;

import org.a0z.mpd.AlbumInfo;
import org.a0z.mpd.MPDCommand;
import org.a0z.mpd.exception.MPDServerException;
import org.a0z.mpd.item.Album;
import org.a0z.mpd.item.AlbumParcelable;
import org.a0z.mpd.item.Item;
import org.a0z.mpd.item.Music;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.widget.PopupMenuCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SongsFragment extends BrowseFragment {

    private static final String EXTRA_ALBUM = "album";
    private static final int POPUP_COVER_BLACKLIST = 5;
    private static final int POPUP_COVER_SELECTIVE_CLEAN = 6;

    private static final String TAG = "SongsFragment";

    Album album = null;
    TextView headerArtist;
    TextView headerInfo;

    private AlbumCoverDownloadListener coverArtListener;
    ImageView coverArt;
    ProgressBar coverArtProgress;

    CoverAsyncHelper coverHelper;
    ImageButton albumMenu;
    PopupMenu popupMenu;
    private PopupMenu coverPopupMenu;

    final MPDApplication app = MPDApplication.getInstance();

    public SongsFragment() {
        super(R.string.addSong, R.string.songAdded, MPDCommand.MPD_SEARCH_TITLE);
    }

    @Override
    protected void add(Item item, boolean replace, boolean play) {
        Music music = (Music) item;
        try {
            app.oMPDAsyncHelper.oMPD.add(music, replace, play);
            Tools.notifyUser(R.string.songAdded, music.getTitle(), music.getName());
        } catch (final MPDServerException e) {
            Log.e(TAG, "Failed to add, remove, play.", e);
        }
    }

    @Override
    protected void add(Item item, String playlist) {
        try {
            app.oMPDAsyncHelper.oMPD.addToPlaylist(playlist, (Music) item);
            Tools.notifyUser(irAdded, item);
        } catch (final MPDServerException e) {
            Log.e(TAG, "Failed to add to playlist.", e);
        }
    }

    @Override
    public void asyncUpdate() {
        try {
            if (getActivity() == null)
                return;
            items = app.oMPDAsyncHelper.oMPD.getSongs(album);
        } catch (final MPDServerException e) {
            Log.e(TAG, "Failed to async update.", e);
        }
    }

    @Override
    protected boolean forceEmptyView() {
        return true;
    }

    @Override
    protected ListAdapter getCustomListAdapter() {
        if (items != null) {
            Music song;
            boolean differentArtists = false;
            String lastArtist = null;
            for (Item item : items) {
                song = (Music) item;
                if (lastArtist == null) {
                    lastArtist = song.getArtist();
                    continue;
                }
                if (!lastArtist.equalsIgnoreCase(song.getAlbumArtistOrArtist())) {
                    differentArtists = true;
                    break;
                }
            }
            return new ArrayAdapter(getActivity(), new SongDataBinder(differentArtists), items);
        }
        return super.getCustomListAdapter();
    }

    private AlbumInfo getFixedAlbumInfo() {
        Music song;
        AlbumInfo albumInfo = null;
        boolean differentArtists = false;

        for (Item item : items) {
            song = (Music) item;
            if (albumInfo == null) {
                albumInfo = song.getAlbumInfo();
                continue;
            }
            String a = albumInfo.getArtist();
            if (a != null && !a.equalsIgnoreCase(song.getAlbumArtistOrArtist())) {
                differentArtists = true;
                break;
            }
        }

        if (differentArtists || albumInfo == null) {
            return new AlbumInfo(getString(R.string.variousArtists), album.getName());
        }
        return albumInfo;
    }

    private String getHeaderInfoString() {
        final int count = items.size();
        return getString(count > 1 ? R.string.tracksInfoHeaderPlural
                        : R.string.tracksInfoHeader, count,
                getTotalTimeForTrackList()
        );
    }

    @Override
    public int getLoadingText() {
        return R.string.loadingSongs;
    }

    @Override
    public String getTitle() {
        final String result;

        if (album == null) {
            result = getString(R.string.songs);
        } else {
            result = album.mainText();
        }

        return result;
    }

    private String getTotalTimeForTrackList() {
        Music song;
        long totalTime = 0;
        for (Item item : items) {
            song = (Music) item;
            if (song.getTime() > 0)
                totalTime += song.getTime();
        }
        return Music.timeToString(totalTime);
    }

    public SongsFragment init(Album al) {
        album = al;
        return this;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null)
            init((Album) icicle.getParcelable(EXTRA_ALBUM));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.songs, container, false);
        list = (ListView) view.findViewById(R.id.list);
        registerForContextMenu(list);
        list.setOnItemClickListener(this);

        loadingView = view.findViewById(R.id.loadingLayout);
        loadingTextView = (TextView) view.findViewById(R.id.loadingText);
        noResultView = view.findViewById(R.id.noResultLayout);
        loadingTextView.setText(getLoadingText());

        final View headerView = inflater.inflate(R.layout.song_header, null, false);
        coverArt = (ImageView) view.findViewById(R.id.albumCover);
        if (coverArt != null) {
            headerArtist = (TextView) view.findViewById(R.id.tracks_artist);
            headerInfo = (TextView) view.findViewById(R.id.tracks_info);
            coverArtProgress = (ProgressBar) view.findViewById(R.id.albumCoverProgress);
            albumMenu = (ImageButton) view.findViewById(R.id.album_menu);
        } else {
            headerArtist = (TextView) headerView.findViewById(R.id.tracks_artist);
            headerInfo = (TextView) headerView.findViewById(R.id.tracks_info);
            coverArt = (ImageView) headerView.findViewById(R.id.albumCover);
            coverArtProgress = (ProgressBar) headerView.findViewById(R.id.albumCoverProgress);
            albumMenu = (ImageButton) headerView.findViewById(R.id.album_menu);
        }

        coverArtListener = new AlbumCoverDownloadListener(coverArt, coverArtProgress, false);
        coverHelper = new CoverAsyncHelper();
        coverHelper.setCoverMaxSizeFromScreen(getActivity());
        final ViewTreeObserver vto = coverArt.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                if (coverHelper != null)
                    coverHelper.setCachedCoverMaxSize(coverArt.getMeasuredHeight());
                return true;
            }
        });
        coverHelper.addCoverDownloadListener(coverArtListener);

        ((TextView) headerView.findViewById(R.id.separator_title)).setText(R.string.songs);
        ((ListView) list).addHeaderView(headerView, null, false);

        popupMenu = new PopupMenu(getActivity(), albumMenu);
        popupMenu.getMenu().add(Menu.NONE, ADD, Menu.NONE, R.string.addAlbum);
        popupMenu.getMenu().add(Menu.NONE, ADDNREPLACE, Menu.NONE, R.string.addAndReplace);
        popupMenu.getMenu().add(Menu.NONE, ADDNREPLACEPLAY, Menu.NONE, R.string.addAndReplacePlay);
        popupMenu.getMenu().add(Menu.NONE, ADDNPLAY, Menu.NONE, R.string.addAndPlay);

        popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final int itemId = item.getItemId();
                app.oMPDAsyncHelper.execAsync(new Runnable() {
                    @Override
                    public void run() {
                        boolean replace = false;
                        boolean play = false;
                        switch (itemId) {
                            case ADDNREPLACEPLAY:
                                replace = true;
                                play = true;
                                break;
                            case ADDNREPLACE:
                                replace = true;
                                break;
                            case ADDNPLAY:
                                play = true;
                                break;
                        }
                        try {
                            app.oMPDAsyncHelper.oMPD.add(album, replace, play);
                            Tools.notifyUser(R.string.albumAdded, album);
                        } catch (final MPDServerException e) {
                            Log.e(TAG, "Failed to add, replace, play.", e);
                        }
                    }
                });
                return true;
            }
        });

        albumMenu.setOnTouchListener(PopupMenuCompat.getDragToOpenListener(popupMenu));
        albumMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                popupMenu.show();
            }
        });

        coverPopupMenu = new PopupMenu(getActivity(), coverArt);
        coverPopupMenu.getMenu().add(POPUP_COVER_BLACKLIST, POPUP_COVER_BLACKLIST, 0,
                R.string.otherCover);
        coverPopupMenu.getMenu().add(POPUP_COVER_SELECTIVE_CLEAN, POPUP_COVER_SELECTIVE_CLEAN, 0,
                R.string.resetCover);
        coverPopupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getGroupId()) {
                    case POPUP_COVER_BLACKLIST:
                        CoverManager.getInstance().markWrongCover(album.getAlbumInfo());
                        updateCover(album.getAlbumInfo());
                        updateNowPlayingSmallFragment(album.getAlbumInfo());
                        break;
                    case POPUP_COVER_SELECTIVE_CLEAN:
                        CoverManager.getInstance().clear(album.getAlbumInfo());
                        updateCover(album.getAlbumInfo());
                        updateNowPlayingSmallFragment(album.getAlbumInfo());
                        break;
                }
                return true;
            }
        });

        coverArt.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                coverPopupMenu.show();
                return false;
            }
        });

        updateFromItems();

        return view;
    }

    @Override
    public void onDestroyView() {
        headerArtist = null;
        headerInfo = null;
        coverArtListener.freeCoverDrawable();
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        coverHelper = null;
        super.onDetach();
    }

    @Override
    public void onItemClick(final AdapterView<?> adapterView, View v, final int position, long id) {
        // If in simple mode : add, replace and play the shown album.
        if (app.isInSimpleMode()) {
            app.oMPDAsyncHelper.execAsync(new Runnable() {
                @Override
                public void run() {
                    try {
                        app.oMPDAsyncHelper.oMPD.add(album, true, true);
                        // Account for the list header
                        int positionCorrection = 0;
                        if (list instanceof ListView) {
                            positionCorrection = ((ListView)list).getHeaderViewsCount();
                        }
                        app.oMPDAsyncHelper.oMPD.seekByIndex(position - positionCorrection, 0l);
                    } catch (final MPDServerException e) {
                        Log.e(TAG, "Failed to seek by index.", e);
                    }
                }
            });
        } else {
            app.oMPDAsyncHelper.execAsync(new Runnable() {
                @Override
                public void run() {
                    add((Item) adapterView.getAdapter().getItem(position), false, false);
                }
            });
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        final Parcelable parcel = new AlbumParcelable(album);
        outState.putParcelable(EXTRA_ALBUM, parcel);
        super.onSaveInstanceState(outState);
    }

    public void updateCover(AlbumInfo albumInfo) {
        if (coverArt != null && null != coverArt.getTag()
                && coverArt.getTag().equals(albumInfo.getKey())) {
            coverHelper.downloadCover(albumInfo, true);
        }
    }

    @Override
    public void updateFromItems() {
        super.updateFromItems();
        if (items != null && headerArtist != null && headerInfo != null) {
            AlbumInfo fixedAlbumInfo;
            fixedAlbumInfo = getFixedAlbumInfo();
            String artist = fixedAlbumInfo.getArtist();
            if (artist.isEmpty()) {
                headerArtist.setText(R.string.unknown_metadata_artist);
            } else {
                headerArtist.setText(artist);
            }
            headerInfo.setText(getHeaderInfoString());
            if (coverHelper != null) {
                coverHelper.downloadCover(fixedAlbumInfo, true);
            } else {
                coverArtListener.onCoverNotFound(new CoverInfo(fixedAlbumInfo));
            }
        }

    }

    private void updateNowPlayingSmallFragment(AlbumInfo albumInfo) {
        NowPlayingSmallFragment nowPlayingSmallFragment;
        if (getActivity() != null) {
            nowPlayingSmallFragment = (NowPlayingSmallFragment) getActivity()
                    .getSupportFragmentManager().findFragmentById(R.id.now_playing_small_fragment);
            if (nowPlayingSmallFragment != null) {
                nowPlayingSmallFragment.updateCover(albumInfo);
            }
        }
    }

}
