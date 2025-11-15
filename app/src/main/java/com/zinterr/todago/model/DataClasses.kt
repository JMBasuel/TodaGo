package com.zinterr.todago.model

import com.google.gson.annotations.SerializedName

// USER ACCOUNT DETAILS MODEL
data class Account(
    val uid: String? = null,
    var name: String? = null,
    val gender: String? = null,
    var phone: String? = null,
    var location: String? = null,
    var email: String? = null,
    var rating: Float? = null,
    var rates: Int? = null,
    var verified: Boolean? = null,
    var emailVerified: Boolean? = null,
    val discount: Discount? = null
)

data class Discount(
    val discounted: Boolean? = null,
    val discountType: String? = null,
    val discountExpire: String? = null
)

// APP UPDATES MODEL
data class App (
    val todaGoVersion: String? = null,
    val todaGoRequired: Boolean? = null,
    val todaGoLink: String? = null,
    val todaGoLog: String? = null
)

// ASSOCIATION PRICE MATRIX MODEL
data class Matrix(
    val discount: Int? = null,
    val perKm: Float? = null,
    val regular: Int? = null,
    val special: Int? = null,
    val specialPerKM: Float? = null
)

// RIDE DETAILS MODEL
data class Commuter(
    val uid: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val rate: String? = null,
    val passenger: Int? = null,
    val start: String? = null,
    val startAddress: String? = null,
    val end: String? = null,
    val endAddress: String? = null,
    val current: String? = null,
    val distance: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val dateTime: String? = null,
    val weight: Int? = null,
    val discount: String? = null,
    val price: Int? = null
)

data class Driver(
    val uid: String? = null,
    val name: String? = null,
    val plate: String? = null,
    val rate: String? = null,
    val current: String? = null,
    val phone: String? = null,
    val profile: String? = null,
    val busy: Boolean? = null,
    val active: Boolean? = null,
    val matrix: Matrix? = null
)

data class Ride(
    val uid: String? = null,
    val solo: Boolean? = null,
    val passenger: Int? = null,
    val commuter: Map<String, Commuter>? = null,
    var status: String? = null,
    val reference: String? = null,
    val dateTime: String? = null,
    val weight: Int? = null,
    val driver: Driver? = null
)

// HISTORY DETAILS MODEL
data class History(
    val uid: String? = null,
    val solo: Boolean? = null,
    val start: String? = null,
    val end: String? = null,
    val passenger: Int? = null,
    val price: Int? = null,
    val driver: String? = null,
    val dateTime: String? = null
)

// NATIONAL ID QR SCANNER RESULT MODEL
data class Subject(
    val fName: String? = null,
    val lName: String? = null,
    val sex: String? = null
)

// DIRECTIONS API RESPONSE MODEL
data class DirectionsResponse(val routes: List<Route>)

data class Route(
    @SerializedName("bounds") val bounds: RouteBounds,
    val legs: List<Leg>
)

data class RouteBounds(
    @SerializedName("northeast") val northeast: LatLngLiteral,
    @SerializedName("southwest") val southwest: LatLngLiteral,
)

data class Leg(
    @SerializedName("start_location") val start: LatLngLiteral,
    @SerializedName("end_location") val end: LatLngLiteral,
    @SerializedName("distance") val distance: ValueText,
    @SerializedName("duration") val duration: ValueText,
    val steps: List<Step>
)

data class Step(val polyline: PolylinePoints)

data class ValueText(val text: String, val value: Int)

data class PolylinePoints(val points: String)

data class LatLngLiteral(val lat: Double, val lng: Double)

// DISTANCE MATRIX RESPONSE MODEL
data class DistanceMatrixResponse(val rows: List<Row>?)

data class Row(val elements: List<Element>?)

data class Element(val duration: Duration?, val distance: Distance?)

data class Duration(val text: String?, val value: Int?)

data class Distance(val text: String?, val value: Int?)

// GEOCODE API RESPONSE MODEL
data class GeocodeResponse(
    val results: List<GeocodeResult>
)

data class GeocodeResult(
    @SerializedName("address_components") val addressComponents: List<AddressComponent>
)

data class AddressComponent(
    @SerializedName("long_name") val longName: String,
    val types: List<String>
)

// GEOCODE API RESPONSE PARSING MODEL
data class LocalAddress(
    val province: String? = null,
    val city: String? = null,
    val locality: String? = null,
    val extra: String? = null
)

