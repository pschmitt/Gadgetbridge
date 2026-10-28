package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentSportListBinding

/**
 * Sports with at least one workout, most workouts first.
 */
class SportListFragment : Fragment() {
    private val viewModel: WorkoutStatisticsViewModel by activityViewModels()

    private var _binding: FragmentSportListBinding? = null
    private val binding get() = _binding!!

    private val adapter = SportListAdapter { sport ->
        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, SportStatisticsFragment.newInstance(sport.kindCode))
            .addToBackStack(null)
            .commit()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSportListBinding.inflate(inflater, container, false)
        binding.sportList.layoutManager = LinearLayoutManager(requireContext())
        binding.sportList.adapter = adapter
        binding.sportList.addItemDecoration(SportRowDividerDecoration(requireContext()))
        binding.sportRetry.setOnClickListener { viewModel.reload() }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.activity_summaries_statistics)
    }

    override fun onDestroyView() {
        binding.sportList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: StatisticsState) {
        val sports = (state as? StatisticsState.Ready)?.sports
        val hasSports = !sports.isNullOrEmpty()

        binding.sportContent.isVisible = state is StatisticsState.Loading || hasSports
        binding.sportLoading.isVisible = state is StatisticsState.Loading
        binding.sportList.isVisible = hasSports
        binding.sportEmpty.isVisible = sports != null && sports.isEmpty()
        binding.sportError.isVisible = state is StatisticsState.Error

        if (sports != null) {
            adapter.submitList(sports)
        }
    }
}
