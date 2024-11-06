package org.staacks.alpharemote.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("NotifyDataSetChanged")
class CustomButtonRecyclerViewAdapter(private val dataSet: MutableStateFlow<List<CameraAction>?>, private val lifecycleOwner: LifecycleOwner, private val customButtonListEventReceiver: CustomButtonListEventReceiver) :
    RecyclerView.Adapter<CustomButtonRecyclerViewAdapter.ViewHolder>() {

    var list: List<CameraAction>? = null

    init {
        lifecycleOwner.lifecycleScope.launch {
            dataSet.collect{
                if (it != null && list == null) {
                    list = it
                    notifyDataSetChanged()
                }
            }
        }
    }

    var context: Context? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val title: TextView = view.findViewById(R.id.title)
        val handle: ImageView = view.findViewById(R.id.handle)
    }

    @SuppressLint("ClickableViewAccessibility") //The onTouchListener is actually about the touchdown event
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.settings_custom_button_row_item, viewGroup, false)
        val viewHolder = ViewHolder(view)
        view.findViewById<ImageView>(R.id.handle).setOnTouchListener { _, event ->
            if (event?.actionMasked == MotionEvent.ACTION_DOWN) {
                customButtonListEventReceiver.startDragging(viewHolder)
            }
            true
        }
        view.setOnClickListener {
            val pos = viewHolder.bindingAdapterPosition
            list?.get(pos)?.let { action ->
                customButtonListEventReceiver.itemTouched(
                    pos, action
                )
            }
        }
        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        list?.get(position)?.let { actionButton ->
            context?.let {ctx ->
                viewHolder.title.text = actionButton.getName(ctx)
                viewHolder.icon.setImageDrawable(actionButton.getIcon(ctx))
            }
        }
    }

    fun moveItem(from: Int, to: Int) {
        lifecycleOwner.lifecycleScope.launch{
            list?.toMutableList()?.let {newList ->
                val item = newList.removeAt(from)
                newList.add(to, item)
                list = newList
                dataSet.emit(list)
                notifyItemMoved(from, to)
            }
        }
    }

    fun removeItem(index: Int) {
        lifecycleOwner.lifecycleScope.launch{
            list?.toMutableList()?.let {newList ->
                newList.removeAt(index)
                list = newList
                dataSet.emit(list)
                notifyItemRemoved(index)
            }
        }
    }

    fun updateItem(index: Int, action: CameraAction) {
        lifecycleOwner.lifecycleScope.launch {
            list?.let {
                if (index < 0) {
                    val pos = it.count()
                    list = it.toMutableList().apply {
                        add(pos, action)
                    }
                    notifyItemInserted(pos)
                } else {
                    list = it.toMutableList().apply {
                        set(index, action)
                    }
                    notifyItemChanged(index)
                }
                dataSet.emit(list)
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = list?.size ?: 0
}