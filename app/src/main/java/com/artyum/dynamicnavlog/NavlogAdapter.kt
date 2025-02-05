package com.artyum.dynamicnavlog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class NavlogAdapter(
    private val navlogList: List<NavlogItemData>, private val listenerClick: OnItemClickInterface, private val listenerLongClick: OnItemLongClickInterface
) : RecyclerView.Adapter<NavlogAdapter.NavlogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavlogViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.fragment_navlog_item, parent, false)
        return NavlogViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: NavlogViewHolder, position: Int) {
        val item = navlogList[position]

        // DEST
        holder.tvDest.text = item.dest

        // TT
        holder.tvTt.text = Utils.formatDouble(item.tt)

        // d
        var tmp = ""
        if (item.d != null) {
            tmp = if (item.d!! < 0.0) Utils.formatDouble(-item.d!!) + C.SIGN_EAST
            else Utils.formatDouble(item.d) + C.SIGN_WEST
        }
        holder.tvDec.text = tmp

        // MT
        holder.tvMt.text = Utils.formatDouble(item.mt)

        // DIST
        tmp = ""
        if (item.dist != null) {
            val p = if (item.dist!! < C.DIST_THRESHOLD) 1 else 0
            tmp = Utils.formatDouble(Units.toUserUnitsDis(item.dist), p)
        }
        holder.tvDist.text = tmp

        if (item.active) {
            //WCA
            holder.tvWca.text = Utils.formatDouble(item.wca)

            //HDG
            holder.tvHdg.text = Utils.formatDouble(item.hdg)

            //GS
            holder.tvGs.text = Utils.formatDouble(Units.toUserUnitsSpd(item.gs))

            // TIME
            if (State.settings.tfDisplayToggle == C.TF_DISPLAY_CUR) holder.tvTime.text = TimeUtils.formatSecondsToTime(item.time)
            else holder.tvTime.text = TimeUtils.formatSecondsToTime(item.timeIncrement)

            // ETA / ATA
            holder.tvEta.text = TimeUtils.formatDateTime(item.eta, C.FORMAT_TIME)
            holder.tvAta.text = TimeUtils.formatDateTime(item.ata, C.FORMAT_TIME)

            // Fuel
            val p1 = if (item.fuel != null && item.fuel!! < C.VOL_THRESHOLD) 1 else 0
            val p2 = if (item.fuelRemaining != null && item.fuelRemaining!! < C.VOL_THRESHOLD) 1 else 0
            if (State.settings.tfDisplayToggle == C.TF_DISPLAY_CUR) holder.tvFuel.text = Utils.formatDouble(Units.toUserUnitsVol(item.fuel), p1)
            else holder.tvFuel.text = Utils.formatDouble(Units.toUserUnitsVol(item.fuelRemaining), p2)
        } else {
            holder.tvWca.text = ""
            holder.tvHdg.text = ""
            holder.tvGs.text = ""
            holder.tvTime.text = ""
            holder.tvEta.text = ""
            holder.tvAta.text = ""
            holder.tvFuel.text = ""
        }

        when {
            item.current -> {
                setColor(holder, item, R.color.colorSecondary)
            }

            position % 2 == 0 -> {
                setColor(holder, item, R.color.color1)
            }

            else -> {
                setColor(holder, item, R.color.color2)
            }
        }
    }

    private fun setColor(holder: NavlogViewHolder, item: NavlogItemData, color: Int) {
        if (item.active) {
            val col = R.color.black
            setContextColor(holder.tvDest, col)
            setContextColor(holder.tvTt, col)
            setContextColor(holder.tvDec, col)
            setContextColor(holder.tvMt, col)
            setContextColor(holder.tvDist, col)
            setContextColor(holder.tvWca, col)
            setContextColor(holder.tvHdg, col)
            setContextColor(holder.tvGs, col)
            setContextColor(holder.tvTime, col)
            setContextColor(holder.tvEta, col)
            setContextColor(holder.tvAta, col)
            setContextColor(holder.tvFuel, col)
        } else {
            val col = R.color.inactive
            setContextColor(holder.tvDest, col)
            setContextColor(holder.tvTt, col)
            setContextColor(holder.tvDec, col)
            setContextColor(holder.tvMt, col)
            setContextColor(holder.tvDist, col)
        }

        setContextBackground(holder.tvDest, color)
        setContextBackground(holder.tvTt, color)
        setContextBackground(holder.tvDec, color)
        setContextBackground(holder.tvMt, color)
        setContextBackground(holder.tvDist, color)
        setContextBackground(holder.tvWca, color)

        if (item.current) {
            setContextBackground(holder.tvHdg, R.color.colorSecondary)

            val col = R.color.white2
            setContextColor(holder.tvDest, col)
            setContextColor(holder.tvTt, col)
            setContextColor(holder.tvDec, col)
            setContextColor(holder.tvMt, col)
            setContextColor(holder.tvDist, col)
            setContextColor(holder.tvWca, col)
            setContextColor(holder.tvHdg, col)
            setContextColor(holder.tvGs, col)
            setContextColor(holder.tvTime, col)
            setContextColor(holder.tvEta, col)
            setContextColor(holder.tvAta, col)
            setContextColor(holder.tvFuel, col)
        } else {
            holder.tvHdg.setBackgroundColor(ContextCompat.getColor(holder.tvDest.context, R.color.hdg))
        }

        setContextBackground(holder.tvGs, color)
        setContextBackground(holder.tvTime, color)
        setContextBackground(holder.tvEta, color)
        setContextBackground(holder.tvAta, color)

        // if fuel < 0 -> warning background
        if (holder.tvFuel.text.contains("-")) setContextBackground(holder.tvFuel, R.color.warning)
        else setContextBackground(holder.tvFuel, color)
    }

    private fun setContextBackground(tv: TextView, color: Int) {
        tv.setBackgroundColor(ContextCompat.getColor(tv.context, color))
    }

    private fun setContextColor(tv: TextView, color: Int) {
        tv.setTextColor(ContextCompat.getColor(tv.context, color))
    }

    override fun getItemCount() = navlogList.size

    inner class NavlogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        val tvDest: TextView = itemView.findViewById(R.id.txtNavlogDest)
        val tvTt: TextView = itemView.findViewById(R.id.txtNavlogTt)
        val tvDec: TextView = itemView.findViewById(R.id.txtNavlogDec)
        val tvMt: TextView = itemView.findViewById(R.id.txtNavlogMt)
        val tvDist: TextView = itemView.findViewById(R.id.txtNavlogDist)
        val tvWca: TextView = itemView.findViewById(R.id.txtNavlogWca)
        val tvHdg: TextView = itemView.findViewById(R.id.txtNavlogHdg)
        val tvGs: TextView = itemView.findViewById(R.id.txtNavlogGs)
        val tvTime: TextView = itemView.findViewById(R.id.txtNavlogTime)
        val tvEta: TextView = itemView.findViewById(R.id.txtNavlogEta)
        val tvAta: TextView = itemView.findViewById(R.id.txtNavlogAta)
        val tvFuel: TextView = itemView.findViewById(R.id.txtNavlogFuel)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listenerClick.onItemClick(position)
            }
        }

        override fun onLongClick(v: View?): Boolean {
            val position = absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listenerLongClick.onItemLongClick(position)
                return true
            }
            return false
        }
    }

    interface OnItemClickInterface {
        fun onItemClick(position: Int)
    }

    interface OnItemLongClickInterface {
        fun onItemLongClick(position: Int)
    }
}