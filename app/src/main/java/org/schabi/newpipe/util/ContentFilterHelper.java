package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.settings.ContentFilterFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ContentFilterHelper {
	private ContentFilterHelper() {
	}

	public static List<ContentFilterFragment.ContentFilterItem> getContentFilterItemList(final Context context) {
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		final String contentFilterListKey = context.getString(R.string.content_filter_list_key);
		final String savedJson = sharedPreferences.getString(contentFilterListKey, null);
		if (null == savedJson) {
			return null;
		}

		try {
			final JsonArray array = JsonParser.object().from(savedJson).getArray("filterItems");
			final List<ContentFilterFragment.ContentFilterItem> result = new ArrayList<>();
			for (final Object o : array) {
				if (o instanceof JsonObject filterItem) {
					final boolean enabled = filterItem.getBoolean("enabled");
					final String filterText = filterItem.getString("filterText");
					result.add(new ContentFilterFragment.ContentFilterItem(filterText, enabled));
				}
			}
			return result;
		} catch (final JsonParserException e) {
			return null;
		}
	}

	public static List<ContentFilterFragment.ContentFilterItem> getEnabledContentFilterItemList(final Context context) {
		List<ContentFilterFragment.ContentFilterItem> filterItemList = getContentFilterItemList(context);
		filterItemList.removeIf(a -> !a.isEnabled());
		return filterItemList;
	}

	public static void saveContentFilterItemList(final Context context, final List<ContentFilterFragment.ContentFilterItem> filterItems) {
		final JsonStringWriter jsonWriter = JsonWriter.string().object().array("filterItems");
		for (final ContentFilterFragment.ContentFilterItem filterItem : filterItems) {
			jsonWriter.object();
			jsonWriter.value("filterText", filterItem.getFilterText());
			jsonWriter.value("enabled", filterItem.isEnabled());
			jsonWriter.end();
		}
		final String jsonToSave = jsonWriter.end().end().done();
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		final String contentFilterListKey = context.getString(R.string.content_filter_list_key);
		sharedPreferences.edit().putString(contentFilterListKey, jsonToSave).apply();
	}

	public static List<InfoItem> contentFilterInfoItems(Context context, List<? extends InfoItem> infoItems) {
		List<InfoItem> data = new ArrayList<>(infoItems);
		List<ContentFilterFragment.ContentFilterItem> filterItemList = ContentFilterHelper.getEnabledContentFilterItemList(context);
		List<InfoItem> removalCandidates = new ArrayList<>();
		for (InfoItem item : data) {
			String textToCheck = item.getName() + "|";
			switch (item.getInfoType()) {
				case STREAM:
					StreamInfoItem streamInfoItem = (StreamInfoItem) item;
					textToCheck += streamInfoItem.getUploaderName() + "|" + streamInfoItem.getShortDescription();
					break;
				case CHANNEL:
					ChannelInfoItem channelInfoItem = (ChannelInfoItem) item;
					textToCheck += channelInfoItem.getDescription();
					break;
				case PLAYLIST:
					PlaylistInfoItem playlistInfoItem = (PlaylistInfoItem) item;
					textToCheck += playlistInfoItem.getUploaderName() + "|" + playlistInfoItem.getDescription();
					break;
				case COMMENT:
					CommentsInfoItem commentsInfoItem = (CommentsInfoItem) item;
					textToCheck += commentsInfoItem.getUploaderName() + "|" + commentsInfoItem.getCommentText().getContent();
					break;
				default:
					break;
			}
			final String finalTextToCheck = textToCheck.toLowerCase();
			if (filterItemList.stream().anyMatch(fi -> finalTextToCheck.contains(fi.getFilterText()))) {
				removalCandidates.add(item);
			}
		}
		data.removeAll(removalCandidates);
		return data;
	}
}
