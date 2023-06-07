package de.rwth_aachen.phyphox.camera.ui

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
    private val dataList: List<String>,
    private val settingChooseListener: SettingChooseListener,
    private var selectedPosition: Int
) : RecyclerView.Adapter<ChooseCameraSettingValueAdapter.ViewHolder>() {

    private val buttonClick = AlphaAnimation(1f, 0.4f)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.camera_setting_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]
        holder.textView.text = item
        holder.textView.animation = buttonClick

        holder.radioButton.text = item

        holder.radioButton.isChecked = position == selectedPosition
        holder.radioButton.setOnClickListener {
            selectedPosition = holder.adapterPosition
            notifyItemChanged(selectedPosition)
            settingChooseListener.onSettingClicked(dataList[position], position)
        }

    }

    override fun getItemCount(): Int {
        return dataList.size
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

