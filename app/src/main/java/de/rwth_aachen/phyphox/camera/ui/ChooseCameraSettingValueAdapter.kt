package de.rwth_aachen.phyphox.camera.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.SettingChooseListener

class ChooseCameraSettingValueAdapter(
    private val dataList: List<String>?,
    private val settingChooseListener: SettingChooseListener,
    private val currentValue: String
) : RecyclerView.Adapter<ChooseCameraSettingValueAdapter.ViewHolder>() {

    private val buttonClick = AlphaAnimation(1f, 0.4f)
    private lateinit var context: Context
    private var trackSelectedItem: MutableMap<String, Boolean> = mutableMapOf()

    init {
        dataList?.map { trackSelectedItem[it] = it == currentValue }
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

        if (trackSelectedItem[item] == true) {
            holder.textView.setBackgroundColor(context.resources.getColor(R.color.phyphox_primary))
        } else {
            holder.textView.setBackgroundColor(context.resources.getColor(R.color.phyphox_black_50))
        }

        holder.textView.animation = buttonClick

        holder.textView.setOnClickListener {

            for ((key, _) in trackSelectedItem) {
                trackSelectedItem[key] = key == item
            }

            notifyDataSetChanged()

            settingChooseListener.onSettingClicked(item ?: "")
        }

    }

    override fun getItemCount(): Int = dataList?.size!!

    override fun getItemId(position: Int): Long = position.toLong()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView
        var cardViewTextSetting: CardView

        init {
            textView = itemView.findViewById(R.id.textSettings)
            cardViewTextSetting = itemView.findViewById(R.id.cardViewTextSetting)
        }
    }
}


