package de.rwth_aachen.phyphox.camera.ui

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.SettingChooseListener

class ChooseCameraSettingValueAdapter(
    private val dataList: List<String>?,
    private val settingChooseListener: SettingChooseListener,
    private var selectedPosition: Int
) : RecyclerView.Adapter<ChooseCameraSettingValueAdapter.ViewHolder>() {

    private val buttonClick = AlphaAnimation(1f, 0.4f)
    private lateinit var context: Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view =
            LayoutInflater.from(context).inflate(R.layout.camera_setting_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList?.get(position)
        holder.textView.text = item
        if (selectedPosition == holder.adapterPosition) {
            holder.textView.setBackgroundColor(context.resources.getColor(R.color.phyphox_primary))
        }
        holder.textView.animation = buttonClick

        holder.textView.setOnClickListener {
            selectedPosition = holder.adapterPosition
            notifyItemChanged(selectedPosition)
            settingChooseListener.onSettingClicked(dataList?.get(position) ?: "", position)
        }

    }

    override fun getItemCount(): Int {
        return dataList?.size!!
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView
        var radioButton: RadioButton

        init {
            textView = itemView.findViewById<TextView>(R.id.textSettings)
            radioButton = itemView.findViewById(R.id.radio_button)
        }

    }
}

