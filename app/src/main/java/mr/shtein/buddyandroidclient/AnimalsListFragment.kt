package mr.shtein.buddyandroidclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import mr.shtein.buddyandroidclient.adapters.AnimalsAdapter
import mr.shtein.buddyandroidclient.model.Animal
import mr.shtein.buddyandroidclient.retrofit.Common
import retrofit2.Response
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.transition.Slide
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.*
import mr.shtein.buddyandroidclient.adapters.OnAnimalCardClickListener
import mr.shtein.buddyandroidclient.exceptions.validate.ServerErrorException
import mr.shtein.buddyandroidclient.model.Coordinates
import mr.shtein.buddyandroidclient.model.LocationState
import mr.shtein.buddyandroidclient.repository.AnimalRepository
import mr.shtein.buddyandroidclient.utils.AnimalDiffUtil
import mr.shtein.buddyandroidclient.utils.WorkFragment
import mr.shtein.buddyandroidclient.utils.SharedPreferences
import mr.shtein.buddyandroidclient.viewmodels.AnimalListViewModel
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.math.floor

const val IS_FROM_CITY = "is_from_city"
private const val LAST_FRAGMENT_KEY = "last_fragment"
private const val ANIMAL_CARD_LABEL = "AnimalsCardFragment"
private const val KENNEL_LABEL = "AddKennelFragment"
private const val USER_PROFILE_LABEL = "UserProfileFragment"
private const val REGISTRATION_LABEL = "UserRegistrationFragment"
private const val LOGIN_LABEL = "LoginFragment"

class AnimalsListFragment : Fragment(), OnAnimalCardClickListener, OnLocationBtnClickListener {

    lateinit var adapter: AnimalsAdapter
    lateinit var animalRecyclerView: RecyclerView
    private lateinit var animalChoiceHorizontalScroll: HorizontalScrollView
    private lateinit var dogChip: Chip
    private lateinit var catChip: Chip
    private lateinit var animalDownloadProgress: ProgressBar
    private lateinit var animalCountText: TextView
    private val animalListViewModel: AnimalListViewModel by lazy {
        ViewModelProvider(this).get(AnimalListViewModel::class.java)
    }
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: SharedPreferences
    private lateinit var coroutine: CoroutineScope
    private val animalRepository: AnimalRepository = AnimalRepository()
    private val cancelTokenSourceForLocation = CancellationTokenSource()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findNavController().addOnDestinationChangedListener { _, destination, _ ->
            val workFragment = makeWorkFragment(destination)
            changeAnimationsWhenNavigate(workFragment)
        }
        if (arguments != null) {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        } else {
            val enterSlide = Slide()
            enterSlide.slideEdge = Gravity.RIGHT
            enterSlide.duration = 300
            enterSlide.interpolator = DecelerateInterpolator()
            enterTransition = enterSlide
        }

        storage = SharedPreferences(requireContext(), SharedPreferences.PERSISTENT_STORAGE_NAME)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        coroutine = CoroutineScope(Dispatchers.Main)

        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                (permissions.containsKey(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        permissions.containsKey(Manifest.permission.ACCESS_COARSE_LOCATION)) -> {
                    setLocationToView()
                }
                else -> {
                    val failureText = getString(R.string.location_failure_text)
                    Toast.makeText(requireContext(), failureText, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun makeWorkFragment(destination: NavDestination): WorkFragment {
        return when(destination.label) {
            ANIMAL_CARD_LABEL -> WorkFragment.ANIMAL_CARD
            KENNEL_LABEL -> WorkFragment.ADD_KENNEL
            USER_PROFILE_LABEL -> WorkFragment.USER_PROFILE
            REGISTRATION_LABEL -> WorkFragment.REGISTRATION
            LOGIN_LABEL -> WorkFragment.LOGIN
            else -> WorkFragment.OTHER
        }
    }

    private fun setLocationToView() {
        try {
            val newAnimalList = changeLocationState(LocationState.SEARCH_STATE)
            diffUtilComparator(animalListViewModel.visibleAnimalList, newAnimalList)
            animalListViewModel.visibleAnimalList = newAnimalList
            val locationTask = fusedLocationClient.getCurrentLocation(
                LocationRequest.PRIORITY_HIGH_ACCURACY,
                cancelTokenSourceForLocation.token
            )
            locationTask.addOnSuccessListener {
                val location = locationTask.result
                val currentCoordinates =
                    Coordinates(location.latitude, location.longitude)
                coroutine.launch {
                    try {
                        val token =
                            storage.readString(SharedPreferences.TOKEN_KEY, "")
                        val distances =
                            getDistancesResult(token, currentCoordinates)
                        showResult(distances)
                    } catch (ex: SocketTimeoutException) {
                        val newAnimalList = changeLocationState(LocationState.BAD_RESULT_STATE)
                        diffUtilComparator(animalListViewModel.visibleAnimalList, newAnimalList)
                        val noInternetText =
                            getString(R.string.internet_failure_text)
                        Toast.makeText(
                            requireContext(), noInternetText, Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            locationTask.addOnFailureListener {
                val newAnimalList = changeLocationState(LocationState.BAD_RESULT_STATE)
                diffUtilComparator(animalListViewModel.visibleAnimalList, newAnimalList)
                val failureText = getString(R.string.location_failure_text)
                Toast.makeText(requireContext(), failureText, Toast.LENGTH_LONG)
                    .show()
            }
        } catch (ex: SecurityException) {
            Log.d("error", "errorrrrrrrrrrrrInSecurity")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val workFragment: WorkFragment? = arguments?.getParcelable(LAST_FRAGMENT_KEY)
        if (workFragment != null) {
            changeAnimationsWhenStartFragment(workFragment)
        }
        val view = inflater.inflate(R.layout.animals_list_fragment, container, false)
        setStatusBarColor(true)
        initViewsAndValues(view)
        setInsetsListener(animalChoiceHorizontalScroll)
        setListeners()
        coroutine.launch {
            val errorText = getString(R.string.server_error_msg)
            try {
                showAnimals()
            } catch (ex: ConnectException) {
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show()
            } catch (ex: SocketTimeoutException) {
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show()
            } catch (ex: ServerErrorException) {
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show()
            }
        }
        return view
    }

    private fun setInsetsListener(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun initViewsAndValues(view: View) {
        animalRecyclerView = view.findViewById(R.id.animal_list)
        animalRecyclerView.setHasFixedSize(true)
        animalRecyclerView.layoutManager = LinearLayoutManager(context)

        adapter = AnimalsAdapter(
            requireContext(),
            animalListViewModel.visibleAnimalList,
            this@AnimalsListFragment,
            this@AnimalsListFragment
        )
        animalRecyclerView.adapter = adapter

        animalCountText = view.findViewById(R.id.animals_list_found_animal_count)
        animalChoiceHorizontalScroll = view.findViewById(R.id.animal_choice_scroll_chips)
        dogChip = view.findViewById(R.id.animals_list_dog_chip)
        catChip = view.findViewById(R.id.animals_list_cat_chip)
        animalDownloadProgress = view.findViewById(R.id.animals_list_search_progress_bar)
    }

    private suspend fun showAnimals() {
        val isLocationPermissionGranted = checkLocationPermission()
        if (animalListViewModel.visibleAnimalList.isEmpty()) {
            animalDownloadProgress.visibility = View.VISIBLE
            animalListViewModel.animalList = animalRepository.getAnimals()
            animalListViewModel.visibleAnimalList =
                animalListViewModel.animalList.toMutableList()
            animalDownloadProgress.visibility = View.INVISIBLE

            showDistanceOrLocationIcon(isLocationPermissionGranted)
            val emptyAnimalList = mutableListOf<Animal>()
            diffUtilComparator(emptyAnimalList, animalListViewModel.visibleAnimalList)
        }
        setAnimalCountText()
    }

    private fun checkLocationPermission(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return fineLocationPermission == PackageManager.PERMISSION_GRANTED
                || coarseLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun showDistanceOrLocationIcon(isLocationPermissionGranted: Boolean) {
        if (isLocationPermissionGranted) {
            setLocationToView()
        }
    }

    private fun changeLocationState(state: LocationState): MutableList<Animal> {
        animalListViewModel.locationState = state
        val newAnimalList = mutableListOf<Animal>()
        animalListViewModel.visibleAnimalList.forEach { animal ->
            val newAnimal = animal.copy()
            newAnimal.locationState = state
            newAnimalList.add(newAnimal)
        }
        return newAnimalList
    }

    private fun diffUtilComparator(
        oldAnimalList: MutableList<Animal>,
        newAnimalList: MutableList<Animal>
    ) {
        val animalDiffUtil = AnimalDiffUtil(oldAnimalList, newAnimalList)
        val diffResult = DiffUtil.calculateDiff(animalDiffUtil)
        adapter.animals = newAnimalList
        animalListViewModel.visibleAnimalList = newAnimalList
        diffResult.dispatchUpdatesTo(adapter)
    }

    private suspend fun getDistancesResult(
        token: String,
        coordinates: Coordinates
    ): Response<HashMap<Int, Int>> = withContext(Dispatchers.IO) {
        val retrofit = Common.retrofitService
        return@withContext retrofit.getAllDistances(
            token,
            coordinates.latitude,
            coordinates.longitude
        )
    }

    private suspend fun showResult(result: Response<HashMap<Int, Int>>) =
        withContext(Dispatchers.Main) {
            when (result.code()) {
                200 -> {
                    val distances = result.body() ?: hashMapOf()
                    animalListViewModel.distances = distances
                    val newAnimalList = setDistancesToAnimals(animalListViewModel.distances)
                    diffUtilComparator(animalListViewModel.visibleAnimalList, newAnimalList)
                    animalListViewModel.visibleAnimalList = newAnimalList
                }
            }
        }

    private fun setDistancesToAnimals(distances: HashMap<Int, Int>): MutableList<Animal> {
        animalListViewModel.locationState = LocationState.DISTANCE_VISIBLE_STATE
        val newAnimalList = mutableListOf<Animal>()
        animalListViewModel.visibleAnimalList.forEach { animal ->
            val newAnimal = animal.copy()
            newAnimal.locationState = animalListViewModel.locationState
            newAnimalList.add(newAnimal)
            val kennel = animal.kennel
            val distance = distances[kennel.id] ?: 0
            distance.let {
                if (distance < 1000) {
                    newAnimal.distance = getString(
                        R.string.animal_row_distance_meter_text,
                        distance
                    )
                } else {
                    val distanceInKm = floor(distance.toDouble() / 100) / 10
                    newAnimal.distance = getString(
                        R.string.animal_row_distance_kilometer_text,
                        distanceInKm.toString()
                    )
                }
            }
        }
        return newAnimalList
    }


    override fun onDestroy() {
        super.onDestroy()
        coroutine.cancel()
        cancelTokenSourceForLocation.cancel()
    }

    override fun onClickToLocationBtn() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewsAndValues(view)
    }

    private fun setAnimalCountText() {
        animalCountText.text = resources.getQuantityString(
            R.plurals.buddy_found_count,
            animalListViewModel.visibleAnimalList.size,
            animalListViewModel.visibleAnimalList.size
        )
    }

    private fun setListeners() {

        dogChip.setOnCheckedChangeListener { chip, isChecked ->
            coroutine.launch {
                val dogsList = filterDogs(animalListViewModel.animalList)

                when (isChecked) {
                    true -> {
                        chip.setTextColor(requireContext().getColor(R.color.white))
                        val oldAnimalList = animalListViewModel.visibleAnimalList.toMutableList()
                        animalListViewModel.visibleAnimalList.addAll(dogsList)
                        animalListViewModel.visibleAnimalList.shuffle()

                        val animalDiffUtil =
                            AnimalDiffUtil(oldAnimalList, animalListViewModel.visibleAnimalList)
                        val diffResult = DiffUtil.calculateDiff(animalDiffUtil)
                        adapter.animals = animalListViewModel.visibleAnimalList
                        diffResult.dispatchUpdatesTo(adapter)
                        setAnimalCountText()
                    }
                    else -> {
                        chip.setTextColor(requireContext().getColor(R.color.cian5))
                        val oldAnimalList = animalListViewModel.visibleAnimalList.toMutableList()
                        animalListViewModel.visibleAnimalList.removeAll(dogsList)
                        animalListViewModel.visibleAnimalList.shuffle()

                        val animalDiffUtil =
                            AnimalDiffUtil(oldAnimalList, animalListViewModel.visibleAnimalList)
                        val diffResult = DiffUtil.calculateDiff(animalDiffUtil)
                        adapter.animals = animalListViewModel.visibleAnimalList
                        diffResult.dispatchUpdatesTo(adapter)
                        setAnimalCountText()
                    }
                }
            }
        }

        catChip.setOnCheckedChangeListener { chip, isChecked ->
            coroutine.launch {
                val catsList = filterCats(animalListViewModel.animalList)
                when (isChecked) {
                    true -> {
                        chip.setTextColor(requireContext().getColor(R.color.white))
                        val oldAnimalList = animalListViewModel.visibleAnimalList.toMutableList()
                        animalListViewModel.visibleAnimalList.addAll(catsList)
                        animalListViewModel.visibleAnimalList.shuffle()

                        val animalDiffUtil =
                            AnimalDiffUtil(oldAnimalList, animalListViewModel.visibleAnimalList)
                        val diffResult = DiffUtil.calculateDiff(animalDiffUtil)
                        adapter.animals = animalListViewModel.visibleAnimalList
                        diffResult.dispatchUpdatesTo(adapter)
                        setAnimalCountText()
                    }
                    else -> {
                        chip.setTextColor(requireContext().getColor(R.color.cian5))
                        val oldAnimalList = animalListViewModel.visibleAnimalList.toMutableList()
                        animalListViewModel.visibleAnimalList.removeAll(catsList)
                        animalListViewModel.visibleAnimalList.shuffle()

                        val animalDiffUtil =
                            AnimalDiffUtil(oldAnimalList, animalListViewModel.visibleAnimalList)
                        val diffResult = DiffUtil.calculateDiff(animalDiffUtil)
                        adapter.animals = animalListViewModel.visibleAnimalList
                        diffResult.dispatchUpdatesTo(adapter)
                        setAnimalCountText()
                    }
                }
            }
        }
    }

    override fun onAnimalCardClick(animal: Animal) {
        val bundle = Bundle()
        bundle.putParcelable("animal", animal)
        changeAnimationsWhenNavigate(WorkFragment.ANIMAL_CARD)
        findNavController().navigate(R.id.action_animalsListFragment_to_animalsCardFragment, bundle)
    }


    private suspend fun filterDogs(listForFilter: List<Animal>): MutableList<Animal> =
        withContext(Dispatchers.IO) {
            val dogsList = mutableListOf<Animal>()
            listForFilter.forEach { animal ->
                when {
                    animal.locationState != animalListViewModel.locationState -> {
                        animal.locationState = animalListViewModel.locationState
                    }
                    animal.distance == "" && animalListViewModel.locationState == LocationState.DISTANCE_VISIBLE_STATE -> {
                        animal.distance = makeDistanceStringForAnimal(animal)
                    }
                }
                if (animal.typeId == 1) {
                    dogsList.add(animal)
                }
            }
            return@withContext dogsList
        }

    private suspend fun filterCats(listForFilter: List<Animal>): MutableList<Animal> =
        withContext(Dispatchers.IO) {
            val catsList = mutableListOf<Animal>()
            listForFilter.forEach { animal ->
                when {
                    animal.locationState != animalListViewModel.locationState -> {
                        animal.locationState = animalListViewModel.locationState
                    }
                    animal.distance == "" && animalListViewModel.locationState == LocationState.DISTANCE_VISIBLE_STATE -> {
                        animal.distance = makeDistanceStringForAnimal(animal)
                    }
                }
                if (animal.typeId == 2) {
                    catsList.add(animal)
                }

            }

            return@withContext catsList
        }

    private fun makeDistanceStringForAnimal(animal: Animal): String {
        val distance: Int = animalListViewModel.distances[animal.kennel.id] ?: 0
        return when {
            distance < 1000 -> {
                getString(R.string.animal_row_distance_meter_text, distance)
            }
            else -> {
                val distanceInKm = floor(distance.toDouble() / 100) / 10
                getString(R.string.animal_row_distance_kilometer_text, distanceInKm.toString())
            }
        }
    }

    private fun changeAnimationsWhenStartFragment(workFragment: WorkFragment) {
        when (workFragment) {
            WorkFragment.ADD_KENNEL -> {
                enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
            }
            WorkFragment.USER_PROFILE -> {
                enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
            }
            WorkFragment.LOGIN -> {
                val enterSlide = Slide()
                enterSlide.slideEdge = Gravity.RIGHT
                enterSlide.duration = 300
                enterSlide.interpolator = DecelerateInterpolator()
                enterTransition = enterSlide
            }

        }
    }

    private fun changeAnimationsWhenNavigate(workFragment: WorkFragment) {
        when(workFragment) {
            WorkFragment.ANIMAL_CARD -> {
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
            }
            WorkFragment.ADD_KENNEL -> {
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
            }
            WorkFragment.USER_PROFILE -> {
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
            }
            else -> {
                val exitSlide = Slide()
                exitSlide.slideEdge = Gravity.LEFT
                exitSlide.duration = 300
                exitSlide.interpolator = DecelerateInterpolator()
                exitTransition = exitSlide
            }
        }
    }
}

interface OnLocationBtnClickListener {
    fun onClickToLocationBtn()
}