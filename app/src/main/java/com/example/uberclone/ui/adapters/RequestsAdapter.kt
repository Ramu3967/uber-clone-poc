package com.example.uberclone.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.uberclone.R
import com.example.uberclone.utils.TaxiConstants
import com.example.uberclone.utils.TaxiConstants.UNIT_KM
import com.example.uberclone.utils.TaxiRequest
import com.google.android.gms.maps.model.LatLng

class RequestsAdapter(
    private var driverLocation: LatLng?,
    private var taxiRequests: List<TaxiRequest>,
    private val clickListener: (des: LatLng) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.RequestsViewHolder>() {
    inner class RequestsViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        fun bind(item: TaxiRequest){
            itemView.findViewById<TextView>(R.id.tv_item_taxi_req).text = driverLocation?.let {
                TaxiConstants.calculateDistance(it, item.location).toString() + UNIT_KM
            } ?: item.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_taxi_request, parent,false)
        return RequestsViewHolder(view)
    }

    override fun getItemCount() = taxiRequests.size

    override fun onBindViewHolder(holder: RequestsViewHolder, position: Int) {
        val taxiRequest = taxiRequests[position]
        holder.bind(taxiRequest)
        holder.itemView.setOnClickListener { clickListener(taxiRequest.location) }
    }

    fun submitList(newTaxiRequests: List<TaxiRequest>, driverUpdatedLocation: LatLng?){
        driverLocation = driverUpdatedLocation
        taxiRequests = newTaxiRequests
        notifyDataSetChanged()
    }
}