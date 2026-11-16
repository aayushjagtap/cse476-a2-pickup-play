package edu.msu.cse476.lakekit

object Config {
    const val STATION_URL = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?product=water_level&station=9087023&datum=IGLD&units=metric&time_zone=gmt&format=json&application=MSU_CSE476&range=144"
    const val STATUS_URL = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?product=water_level&station=9087023&datum=IGLD&units=metric&time_zone=gmt&format=json&application=MSU_CSE476&date=latest"
    const val PREFS = "lakekit"
}
