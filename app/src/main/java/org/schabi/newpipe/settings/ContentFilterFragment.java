package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogHelpEditTextBinding;
import org.schabi.newpipe.databinding.FragmentContentFilterListBinding;
import org.schabi.newpipe.databinding.ItemContentFilterBinding;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ContentFilterHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ContentFilterFragment extends Fragment {
	private String savedContentFilterListKey;
	private ContentFilterItemListAdapter filterItemListAdapter;
	private FragmentContentFilterListBinding binding;
	private SharedPreferences sharedPreferences;
	private CompositeDisposable disposables = new CompositeDisposable();
	private ArrayList<ContentFilterItem> allContentFilterItems;
	private CharSequence currentSearchConstraint;

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

	@Override
	public void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
		savedContentFilterListKey = getString(R.string.content_filter_list_key);

		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
							 final Bundle savedInstanceState) {
		binding = FragmentContentFilterListBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull final View rootView,
							  @Nullable final Bundle savedInstanceState) {
		super.onViewCreated(rootView, savedInstanceState);

		binding.contentFilterHelpTV.setText(getString(R.string.content_filter_summary));
		binding.addContentFilterItemButton.setOnClickListener(v -> showAddItemDialog(requireContext()));
		binding.filterItems.setLayoutManager(new LinearLayoutManager(requireContext()));

		final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
		itemTouchHelper.attachToRecyclerView(binding.filterItems);

		allContentFilterItems = (ArrayList<ContentFilterItem>) ContentFilterHelper.getContentFilterItemList(requireContext());
		filterItemListAdapter = new ContentFilterItemListAdapter(requireContext(), itemTouchHelper, allContentFilterItems);
		binding.filterItems.setAdapter(filterItemListAdapter);
		filterItemListAdapter.submitList(allContentFilterItems);

		binding.filterSearchBar.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				currentSearchConstraint = charSequence;
				filterItemListAdapter.getFilter().filter(currentSearchConstraint);
				// https://stackoverflow.com/questions/2718202/custom-filtering-in-android-using-arrayadapter/2726348#2726348
			}

			@Override
			public void afterTextChanged(Editable editable) {
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		ThemeHelper.setTitleToAppCompatActivity(getActivity(),
				getString(R.string.content_filter_title));
	}

	@Override
	public void onPause() {
		super.onPause();
		saveChanges();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (disposables != null) {
			disposables.clear();
		}
		disposables = null;
	}

	@Override
	public void onDestroyView() {
		binding = null;
		super.onDestroyView();
	}

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

	@Override
	public void onCreateOptionsMenu(@NonNull final Menu menu,
									@NonNull final MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_chooser_fragment, menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.menu_item_restore_default) {
			restoreDefaults();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

	private void toggleFilterItem() {
		sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, true).apply();
	}

	private void saveChanges() {
		ContentFilterHelper.saveContentFilterItemList(requireContext(), allContentFilterItems);
	}

	private void restoreDefaults() {
		final Context context = requireContext();
		new AlertDialog.Builder(context)
				.setTitle(R.string.restore_defaults)
				.setMessage(R.string.restore_defaults_confirmation)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok, (dialog, which) -> {
					sharedPreferences.edit().remove(savedContentFilterListKey).apply();
					allContentFilterItems = (ArrayList<ContentFilterItem>) ContentFilterHelper.getContentFilterItemList(requireContext());
					filterItemListAdapter.setAllFilterItems(allContentFilterItems);
					filterItemListAdapter.submitList(allContentFilterItems);
				})
				.show();
	}

	private void showAddItemDialog(final Context c) {
		final DialogHelpEditTextBinding dialogBinding = DialogHelpEditTextBinding.inflate(getLayoutInflater());
		dialogBinding.helpTextView.setText(R.string.content_filter_add_help);
		dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
		dialogBinding.dialogEditText.setHint(R.string.content_filter_add_hint);
		dialogBinding.dialogEditText.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				inputDone(dialogBinding);
				return true;
			}
			return false;
		});

		AlertDialog contentFilterInputDialog = new AlertDialog.Builder(c)
				.setTitle(R.string.content_filter_add_title)
				.setView(dialogBinding.getRoot())
				.setNegativeButton(R.string.cancel, null)
				.setNeutralButton(R.string.add, null)
				.setPositiveButton(R.string.ok, (dialog1, which) -> {
					inputDone(dialogBinding);
				})
				.show();

		// separate set of OnClickListener prevents dismiss after click
		contentFilterInputDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener((dialog) -> {
			inputDone(dialogBinding);
			dialogBinding.dialogEditText.setText(null);
		});
	}

	private void inputDone(DialogHelpEditTextBinding dialogBinding) {
		final String filterText = dialogBinding.dialogEditText.getText().toString().strip();
		if (!filterText.isEmpty()) {
			addFilterItem(filterText.toLowerCase());
		}
	}

	private void addFilterItem(final String filterText) {
		if (allContentFilterItems.stream().anyMatch(c -> c.filterText.equals(filterText))) {
			Toast.makeText(getActivity(), R.string.content_filter_item_exists, Toast.LENGTH_SHORT).show();
		} else {
			final ContentFilterItem filterItem = new ContentFilterItem(filterText);
			allContentFilterItems.add(filterItem);
			filterItemListAdapter.setAllFilterItems(allContentFilterItems);
			filterItemListAdapter.getFilter().filter(currentSearchConstraint);
		}
	}

	private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
		return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
				ItemTouchHelper.START | ItemTouchHelper.END) {
			@Override
			public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
													final int viewSize,
													final int viewSizeOutOfBounds,
													final int totalSize,
													final long msSinceStartScroll) {
				final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
						viewSizeOutOfBounds, totalSize, msSinceStartScroll);
				final int minimumAbsVelocity = Math.max(12, Math.abs(standardSpeed));
				return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
			}

			@Override
			public boolean onMove(@NonNull final RecyclerView recyclerView,
								  @NonNull final RecyclerView.ViewHolder source,
								  @NonNull final RecyclerView.ViewHolder target) {
				return source.getItemViewType() == target.getItemViewType()
						&& filterItemListAdapter != null;
			}

			@Override
			public boolean isLongPressDragEnabled() {
				return false;
			}

			@Override
			public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
								 final int swipeDir) {
				final int position = viewHolder.getBindingAdapterPosition();
				allContentFilterItems.removeIf(c -> c.filterText.equals(filterItemListAdapter.getCurrentList().get(position).filterText));
				filterItemListAdapter.setAllFilterItems(allContentFilterItems);
				filterItemListAdapter.getFilter().filter(currentSearchConstraint);
			}
		};
	}

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

	private static class ContentFilterItemListAdapter
			extends ListAdapter<ContentFilterItem, ContentFilterItemListAdapter.TabViewHolder> implements Filterable {
		private final LayoutInflater inflater;
		private final ItemTouchHelper itemTouchHelper;
		private Filter filter;
		private List<ContentFilterItem> allFilterItems;

		ContentFilterItemListAdapter(final Context context, final ItemTouchHelper itemTouchHelper, List<ContentFilterItem> allFilterItems) {
			super(new ContentFilterItemCallback());
			this.itemTouchHelper = itemTouchHelper;
			this.inflater = LayoutInflater.from(context);
			this.allFilterItems = allFilterItems;
		}

		@NonNull
		@Override
		public TabViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
												final int viewType) {
			return new TabViewHolder(ItemContentFilterBinding.inflate(inflater,
					parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull final TabViewHolder holder,
									 final int position) {
			holder.bind(position);
		}

		public List<ContentFilterItem> getAllFilterItems() {
			return allFilterItems;
		}

		public void setAllFilterItems(List<ContentFilterItem> allFilterItems) {
			this.allFilterItems = allFilterItems;
		}

		class TabViewHolder extends RecyclerView.ViewHolder {
			private final ItemContentFilterBinding itemBinding;

			TabViewHolder(final ItemContentFilterBinding binding) {
				super(binding.getRoot());
				this.itemBinding = binding;
			}

			void bind(final int position) {
				final ContentFilterItem filterItem = getItem(position);
				itemBinding.filterText.setText(filterItem.getFilterText());
				itemBinding.selectFilterCB.setOnCheckedChangeListener(null);
				itemBinding.selectFilterCB.setChecked(filterItem.isEnabled());
				itemBinding.selectFilterCB.setOnCheckedChangeListener((buttonView, isChecked) -> {
					filterItem.enabled = isChecked;
				});
				itemBinding.getRoot().setOnClickListener(v -> {
					itemBinding.selectFilterCB.toggle();
				});
			}
		}

		public Filter getFilter() {
			if (filter == null) {
				filter = new ContentFilterItemFilter();
			}
			return filter;
		}

		class ContentFilterItemFilter extends Filter {

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				FilterResults filterResults = new FilterResults();

				if (constraint == null || constraint.length() == 0) {
					filterResults.count = getAllFilterItems().size();
					filterResults.values = getAllFilterItems();
				} else {
					String searchStr = constraint.toString().toLowerCase();
					List<ContentFilterItem> collected = getAllFilterItems().stream().filter(c -> c.filterText.contains(searchStr)).collect(Collectors.toList());
					filterResults.count = collected.size();
					filterResults.values = collected;
				}
				return filterResults;
			}

			@Override
			protected void publishResults(CharSequence constraint, FilterResults results) {
				submitList((List<ContentFilterItem>) results.values);
			}
		}
	}

	private static class ContentFilterItemCallback extends DiffUtil.ItemCallback<ContentFilterItem> {
		@Override
		public boolean areItemsTheSame(@NonNull final ContentFilterItem oldItem,
									   @NonNull final ContentFilterItem newItem) {
			return oldItem.getFilterText().equals(newItem.getFilterText());
		}

		@Override
		public boolean areContentsTheSame(@NonNull final ContentFilterItem oldItem,
										  @NonNull final ContentFilterItem newItem) {
			return oldItem.getFilterText().equals(newItem.getFilterText());
		}
	}

	public static class ContentFilterItem {
		private String filterText;
		private boolean enabled = true;

		public ContentFilterItem(String filterText) {
			this.filterText = filterText.toLowerCase();
		}

		public ContentFilterItem(String filterText, boolean enabled) {
			this.filterText = filterText.toLowerCase();
			this.enabled = enabled;
		}

		public String getFilterText() {
			return filterText;
		}

		public boolean isEnabled() {
			return enabled;
		}
	}
}
