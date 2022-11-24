package mr.shtein.buddyandroidclient.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import mr.shtein.buddyandroidclient.R
import mr.shtein.data.model.Animal
import mr.shtein.network.ImageLoader

class DogPhotoAdapter(
    private val animalsList: List<mr.shtein.data.model.Animal>,
    val token: String,
    private val animalTouchCallback: OnAnimalItemClickListener,
    private val networkImageLoader: ImageLoader
): RecyclerView.Adapter<DogPhotoAdapter.AnimalInKennelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalInKennelViewHolder {
        val inflater: LayoutInflater = LayoutInflater.from(parent.context)
        return AnimalInKennelViewHolder(
            inflater.inflate(R.layout.animal_in_kennel_row, parent, false),
            animalTouchCallback
        )
    }

    override fun onBindViewHolder(holder: AnimalInKennelViewHolder, position: Int) {
        val animalCard = getItem(position)
        holder.bind(animalCard)
    }

    override fun getItemCount(): Int {
        return animalsList.size
    }

    private fun getItem(position: Int): mr.shtein.data.model.Animal = animalsList[position]



    inner class AnimalInKennelViewHolder(
        private val itemView: View,
        private val onItemListener: OnAnimalItemClickListener
    ): RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private val avatar = itemView.findViewById<ImageButton>(R.id.animal_in_kennel_avatar)

        init {
            avatar.setOnClickListener(this)
        }

        fun bind(animalCard: mr.shtein.data.model.Animal) {
            val animalAvatarUrl = animalCard.imgUrl.find {
                it.primary
            }
            val endpoint = itemView.resources.getString(R.string.animal_photo_endpoint)
            val dogPlaceholder = itemView.context.getDrawable(R.drawable.light_dog_placeholder)
            networkImageLoader.setPhotoToView(
                avatar,
                endpoint,
                animalAvatarUrl?.url!!,
                dogPlaceholder!!
            )
        }

        override fun onClick(view: View?) {
            onItemListener.onClick(animalsList[absoluteAdapterPosition])
        }
    }

    interface OnAnimalItemClickListener {
        fun onClick(animalItem: mr.shtein.data.model.Animal)
    }
}