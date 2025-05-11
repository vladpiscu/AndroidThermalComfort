import requests
import json
from typing import List

def get_pmv_relevant_weather_data(latitude: float, longitude: float, start_date: str, end_date: str) -> List[dict]:
    """
    Fetches and processes historical environmental data relevant for PMV calculation
    (Air Temperature, Relative Humidity, Wind Speed) for a given date range
    from the Open-Meteo Historical Forecast API.

    Args:
        latitude (float): Latitude of the location.
        longitude (float): Longitude of the location.
        start_date (str): Start date in YYYY-MM-DD format.
        end_date (str): End date in YYYY-MM-DD format.

    Returns:
        list[dict] | None: A list of dictionaries, where each dictionary represents an hour
              and contains 'timestamp_utc', 'air_temp_c', 'humidity_pct', 'wind_speed_ms'.
              Returns None if an error occurs during fetching or processing.
    """
    # Specific endpoint for the Historical Forecast API
    base_url = "https://historical-forecast-api.open-meteo.com/v1/forecast"

    # Define the hourly variables needed
    hourly_variables = [
        "temperature_2m",
        "relative_humidity_2m",
        "wind_speed_10m",
        "soil_temperature_0cm",
        "cloud_cover"
    ]

    # Define API parameters
    params = {
        "latitude": latitude,
        "longitude": longitude,
        "start_date": start_date,
        "end_date": end_date,
        "hourly": ",".join(hourly_variables),
        "wind_speed_unit": "ms",
        "timezone": "UTC" # Requesting UTC simplifies timestamp handling
    }

    print(f"Fetching PMV-relevant weather data from {start_date} to {end_date} UTC...")
    processed_data = [] # To store the structured hourly data

    try:
        # Make the API request
        response = requests.get(base_url, params=params)
        response.raise_for_status() # Raise HTTPError for bad responses (4xx or 5xx)
        weather_data = response.json() # Parse the JSON response

        # --- Data Validation and Processing ---
        if ("hourly" in weather_data and
            "time" in weather_data["hourly"] and
            "temperature_2m" in weather_data["hourly"] and
            "relative_humidity_2m" in weather_data["hourly"] and
            "wind_speed_10m" in weather_data["hourly"] and
            "soil_temperature_0cm" in weather_data["hourly"] and
            "cloud_cover" in weather_data["hourly"]):

            times = weather_data["hourly"]["time"]
            temps = weather_data["hourly"]["temperature_2m"]
            rhums = weather_data["hourly"]["relative_humidity_2m"]
            winds_ms = weather_data["hourly"]["wind_speed_10m"]
            soil_temps = weather_data["hourly"]["soil_temperature_0cm"]
            cloud_covers = weather_data["hourly"]["cloud_cover"]

            print(f"Processing {len(times)} hourly records...")

            for i, timestamp in enumerate(times):
                # Convert wind speed km/h to m/s
                wind_ms = winds_ms[i] * 1000 / 3600 if winds_ms[i] is not None else None
                temp_c = temps[i]
                rh_pct = rhums[i]

                # Basic check for missing data for the hour
                if None in [timestamp, temp_c, rh_pct, wind_ms]:
                    print(f"Warning: Skipping hour {timestamp} due to missing data.")
                    continue

                processed_data.append({
                    "timestamp_utc": timestamp,
                    "air_temp_c": temp_c,
                    "humidity_pct": rh_pct,
                    "wind_speed_ms": wind_ms,
                    "soil_temp_c": soil_temps[i],
                    "cloud_cover": cloud_covers[i]
                })

            print("Weather data processed successfully.")
            return processed_data

        else:
             print("Error: Response missing expected hourly data arrays.")
             return None # Indicate failure if essential data structure is missing

    # --- Error Handling ---
    except requests.exceptions.RequestException as e:
        print(f"Error fetching data from Open-Meteo API: {e}")
        try:
            error_details = response.json()
            print(f"API Error Reason: {error_details.get('reason', 'No reason provided')}")
        except: pass
        return None
    except json.JSONDecodeError:
        print("Error decoding JSON response from Open-Meteo.")
        return None
    except Exception as e:
        print(f"An unexpected error occurred during processing: {e}")
        return None

# Updated helper function to calculate clothing EXACTLY like the CBE snippet
def calculate_dynamic_clothing_cbe(temp_6am_celsius: float) -> float:
    """
    Calculates the dynamic clothing insulation level (clo) based on the
    outdoor air temperature at 6:00 AM, precisely matching the logic
    provided from the CBE Comfort Tool's comf.schiavonClo function.

    Args:
        temp_6am_celsius: The outdoor air temperature at 6:00 AM in degrees Celsius.

    Returns:
        The calculated clothing insulation level in clo units.
    """
    if temp_6am_celsius < -5.0:
        clo_r = 1.0
    elif temp_6am_celsius < 5.0:  # Covers range: -5.0 <= temp_6am_celsius < 5.0
        clo_r = 0.818 - 0.0364 * temp_6am_celsius
    elif temp_6am_celsius < 26.0: # Covers range: 5.0 <= temp_6am_celsius < 26.0
        # Use 10**x which is equivalent to Math.pow(10, x)
        clo_r = 10**(-0.1635 - 0.0066 * temp_6am_celsius)
        # Alternatively: clo_r = math.pow(10, -0.1635 - 0.0066 * temp_6am_celsius)
    else: # Covers range: temp_6am_celsius >= 26.0
        clo_r = 0.46

    # Optional: Add bounds just in case, although the logic seems robust
    # clo_r = max(0.0, min(2.0, clo_r))

    return clo_r

# Combined function (calls the updated clothing calculation)
def get_dynamic_clothing_for_date(latitude: float, longitude: float, date_str: str) -> float:
    """
    Fetches the 6:00 AM outdoor temperature for a specific date and location
    from the Open-Meteo Historical Forecast API and calculates the dynamic
    clothing insulation level (clo) using the CBE tool's logic.

    Args:
        latitude: Latitude of the location.
        longitude: Longitude of the location.
        date_str: The specific date in "YYYY-MM-DD" format.

    Returns:
        The calculated clothing insulation (clo) for 6:00 AM on that date,
        or None if the temperature data cannot be fetched or found.
    """
    base_url = "https://historical-forecast-api.open-meteo.com/v1/forecast"
    params = {
        "latitude": latitude,
        "longitude": longitude,
        "start_date": date_str,
        "end_date": date_str,
        "hourly": "temperature_2m",
        "timezone": "UTC"
    }
    temp_6am = None

    try:
        response = requests.get(base_url, params=params)
        response.raise_for_status()
        weather_data = response.json()

        if "hourly" in weather_data and "time" in weather_data["hourly"] and "temperature_2m" in weather_data["hourly"]:
            times = weather_data["hourly"]["time"]
            temperatures = weather_data["hourly"]["temperature_2m"]
            target_time_str = f"{date_str}T06:00"
            try:
                index_6am = times.index(target_time_str)
                temp_6am = temperatures[index_6am]
                print(f"Found 6:00 AM temperature: {temp_6am}°C")
            except (ValueError, IndexError):
                print(f"Could not find or access 6:00 AM timestamp ('{target_time_str}') in API response.")
        else:
            print("Hourly temperature data not found in API response.")

    except requests.exceptions.RequestException as e:
        print(f"Error fetching data from Open-Meteo API: {e}")
        try:
            error_details = response.json()
            print(f"API Error Reason: {error_details.get('reason', 'No reason provided')}")
        except (json.JSONDecodeError, AttributeError, NameError):
             pass
        return None
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return None

    if temp_6am is not None:
        # Call the updated CBE-specific clothing calculation function
        calculated_clo = calculate_dynamic_clothing_cbe(temp_6am)
        return calculated_clo
    else:
        return None

