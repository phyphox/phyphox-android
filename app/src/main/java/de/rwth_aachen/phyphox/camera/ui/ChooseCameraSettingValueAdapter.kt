package de.rwth_aachen.phyphox.camera.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.SettingChooseListener

class ChooseCameraSettingValueAdapter(
    private val dataList: List<String>?,
    private val settingChooseListener: SettingChooseListener,
) : RecyclerView.Adapter<ChooseCameraSettingValueAdapter.ViewHolder>() {

    private val buttonClick = AlphaAnimation(1f, 0.4f)
    private lateinit var context: Context

    var tracker: SelectionTracker<Long>? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view =
            LayoutInflater.from(context).inflate(R.layout.camera_setting_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList?.get(position)
        holder.textView.text = item

        if(tracker!!.isSelected(position.toLong())){
            holder.textView.setBackgroundColor(context.resources.getColor(R.color.phyphox_primary))
        } else {
            holder.textView.setBackgroundColor(context.resources.getColor(R.color.phyphox_black_100))
        }

        holder.textView.animation = buttonClick

        holder.textView.setOnClickListener {

            tracker?.select(position.toLong())

            settingChooseListener.onSettingClicked(dataList?.get(position) ?: "")
        }

    }

    override fun getItemCount(): Int {
        return dataList?.size!!
    }

    override fun getItemId(position: Int): Long = position.toLong()

    fun selectInitialItem(initialSelectedItemPosition: Int) {
        if (initialSelectedItemPosition != RecyclerView.NO_POSITION) {
            tracker?.select(initialSelectedItemPosition.toLong())
        }
    }


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView

        init {
            textView = itemView.findViewById(R.id.textSettings)
        }

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object :  ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = adapterPosition
                override fun getSelectionKey(): Long = itemId
            }

    }

}

class MyItemDetailsLookup(private val recyclerView: RecyclerView) :
    ItemDetailsLookup<Long>() {
    override fun getItemDetails(event: MotionEvent): ItemDetails<Long>? {
        val view = recyclerView.findChildViewUnder(event.x, event.y)
        if (view != null) {
            return (recyclerView.getChildViewHolder(view) as ChooseCameraSettingValueAdapter.ViewHolder).getItemDetails()
        }
        return null
    }
}

class MyKeyProvider(private val adapter: ChooseCameraSettingValueAdapter) :
    ItemKeyProvider<Long>(SCOPE_CACHED) {

    override fun getKey(position: Int): Long {
        return position.toLong()
    }

    override fun getPosition(key: Long): Int {
        return key.toInt()
    }
}


