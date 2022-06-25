package com.artyum.dynamicnavlog

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.File

class AirplaneListAdapter(
    private val airplaneList: List<Airplane>,
    private val listenerClick: OnItemClickInterface
) : RecyclerView.Adapter<AirplaneListAdapter.AirplaneListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AirplaneListAdapter.AirplaneListViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.fragment_airplanelist_item, parent, false)
        return AirplaneListViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AirplaneListViewHolder, position: Int) {
        holder.id.text = airplaneList[position].id
        holder.type.text = airplaneList[position].type
        holder.reg.text = airplaneList[position].reg
    }

    override fun getItemCount(): Int = airplaneList.size

    inner class AirplaneListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val id: TextView = itemView.findViewById(R.id.txtAirID)
        val type: TextView = itemView.findViewById(R.id.txtAirType)
        val reg: TextView = itemView.findViewById(R.id.txtAirReg)

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