import io
from dap_project import get_pmv_relevant_weather_data, get_dynamic_clothing_for_date
import json

def plot(formattedDate, currentHour, latitude, longitude, averageHeartRate, thermalComfort):
    latitude = 46.770439
    longitude = 23.591423

    # Fetch the available environmental data
    pmv_weather_data = get_pmv_relevant_weather_data(latitude, longitude, formattedDate, formattedDate)
    dynamic_clo = get_dynamic_clothing_for_date(latitude, longitude, formattedDate)

    if pmv_weather_data:
        print("Successfully fetched PMV-relevant weather data:")
        print(json.dumps(pmv_weather_data, indent=2))

        # Example: Access hourly air temperature data
        # hourly_temps = pmv_weather_data.get("hourly", {}).get("temperature_2m")
        # if hourly_temps:
        #    print(f"\nHourly Air Temperatures (°C): {hourly_temps[:5]}...") # Print first 5 values

        print("\nReminder: Mean Radiant Temperature, Clothing Insulation, and Metabolic Rate")
        print("must be determined separately for full PMV calculation.")

    else:
        print("Failed to fetch PMV-relevant weather data.")

    if dynamic_clo:
        print("Successfully fetched Dynamic clothing data:")                                                                                   
        print(dynamic_clo)
    else:
        print("Failed to fetch Dynamic clothing data.")
    currentHourData = pmv_weather_data[currentHour]
    currentHourData["dynamic_clothing"] = dynamic_clo
    return currentHourData
