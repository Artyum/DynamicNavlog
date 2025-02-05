package com.artyum.dynamicnavlog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlanListAdapter(
    private val planList: List<PlanListItemData>,
    private val listenerClick: OnItemClickInterface,
) : RecyclerView.Adapter<PlanListAdapter.PlanListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanListAdapter.PlanListViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.fragment_planlist_item, parent, false)
        return PlanListViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PlanListViewHolder, position: Int) {
        holder.id.text = planList[position].id
        holder.planName.text = FileUtils.getPlanNameFromJson(planList[position].id)
    }

    override fun getItemCount(): Int = planList.size

    inner class PlanListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val id: TextView = itemView.findViewById(R.id.txtID)
        val planName: TextView = itemView.findViewById(R.id.txtListItem)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listenerClick.onItemClick(position)
            }
        }
    }

    interface OnItemClickInterface {
        fun onItemClick(position: Int)
    }
}