package external;

import java.util.*;
//call 外部接口
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entity.Item;
import entity.Item.ItemBuilder;

public class TicketMasterAPI {
	private static final String URL = "https://app.ticketmaster.com/discovery/v2/events.json"; // url
	private static final String DEFAULT_KEYWORD = ""; // default: no key word
	private static final String API_KEY = "5dvNL3GAWyBY7ngZkiyrTsfCavbYoCOY";

	public List<Item> search(double lat, double lon, String keyword) { // debug function
		if (keyword == null) {
			keyword = DEFAULT_KEYWORD;
		}

		try {
			keyword = java.net.URLEncoder.encode(keyword, "UTF-8"); // 转码 变成浏览器认识的格式 Rick sum =》Rick20%sum
		} catch (Exception e) {
			e.printStackTrace();
		}

		String geoHash = GeoHash.encodeGeohash(lat, lon, 9);
		String query = String.format("apikey=%s&geoPoint=%s&keyword=%s&radius=%s", API_KEY, geoHash, keyword, 50); // %s
																													// 占个位置
																													// not
																													// add
																													// space

		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(URL + "?" + query).openConnection(); // 打开链接
			connection.setRequestMethod("GET");

			// 同步 一行行执行 TCP IP 三次握手
			int responseCode = connection.getResponseCode(); // first send request then get result wait until response
			System.out.println(responseCode); // if 200, success start with 2XX see whether 成功

			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			// 对应client输入 传给server 然后返回http的response BufferedRead一行行读出来因为JSON都是String
			// 站在client端
			// getInputStream 拿出response的body部分了
			String inputLine;
			StringBuilder response = new StringBuilder(); // stringbuffer 线程安全 多线程
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			// will return links embedded(events) page
			in.close();

			JSONObject obj = new JSONObject(response.toString());
			if (obj.isNull("_embedded")) {
				return new ArrayList<>();
			}
			JSONObject embedded = obj.getJSONObject("_embedded");
			JSONArray events = embedded.getJSONArray("events");
			return getItemList(events);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	/**
	 * Helper methods
	 */

	// {
	// "name": "laioffer",
	// "id": "12345",
	// "url": "www.laioffer.com",
	// ...
	// "_embedded": {
	// "venues": [
	// {
	// "address": {
	// "line1": "101 First St,",
	// "line2": "Suite 101",
	// "line3": "...",
	// },
	// "city": {
	// "name": "San Francisco"
	// }
	// ...
	// },
	// ...
	// ]
	// }
	// ...
	// }
	private String getAddress(JSONObject event) throws JSONException {
		if (!event.isNull("_embedded")) {
			JSONObject embedded = event.getJSONObject("_embedded");

			if (!embedded.isNull("venues")) {
				JSONArray venues = embedded.getJSONArray("venues");
				for (int i = 0; i < venues.length(); ++i) {
					JSONObject venue = venues.getJSONObject(i);

					StringBuilder sb = new StringBuilder();

					if (!venue.isNull("address")) {
						JSONObject address = venue.getJSONObject("address");
						if (!address.isNull("line1")) {
							sb.append(address.getString("line1"));
						}
						if (!address.isNull("line2")) {
							sb.append(address.getString("line2"));
						}
						if (!address.isNull("line3")) {
							sb.append(address.getString("line3"));
						}
						sb.append(",");
					}

					if (!venue.isNull("city")) {
						JSONObject city = venue.getJSONObject("city");
						if (!city.isNull("name")) {
							sb.append(city.getString("name"));
						}
					}

					if (!sb.toString().equals("")) {
						return sb.toString();
					}
				}
			}
		}

		return "";
	}

	// {"images": [{"url": "www.example.com/my_image.jpg"}, ...]}
	private String getImageUrl(JSONObject event) throws JSONException {
		if (!event.isNull("images")) {
			JSONArray array = event.getJSONArray("images");
			for (int i = 0; i < array.length(); i++) {
				JSONObject image = array.getJSONObject(i);
				if (!image.isNull("url")) {
					return image.getString("url");
				}
			}
		}
		return "";
	}

	// {"classifications" : [{"segment": {"name": "music"}}, ...]}
	private Set<String> getCategories(JSONObject event) throws JSONException {
		Set<String> categories = new HashSet<>();
		if (!event.isNull("classifications")) {
			JSONArray classifications = event.getJSONArray("classifications");
			for (int i = 0; i < classifications.length(); i++) {
				JSONObject classification = classifications.getJSONObject(i);
				if (!classification.isNull("segment")) {
					JSONObject segment = classification.getJSONObject("segment");
					if (!segment.isNull("name")) {
						String name = segment.getString("name");
						categories.add(name);
					}
				}
			}
		}
		return categories;
	}

	// Convert JSONArray to a list of item objects.
	private List<Item> getItemList(JSONArray events) throws JSONException { // 从JSONArray中拿出event（每个object是一个事件），转成Item
		List<Item> itemList = new ArrayList<>();

		for (int i = 0; i < events.length(); ++i) {
			JSONObject event = events.getJSONObject(i);

			ItemBuilder builder = new ItemBuilder();

			if (!event.isNull("name")) {
				builder.setName(event.getString("name"));
			}
			if (!event.isNull("id")) {
				builder.setItemId(event.getString("id"));
			}
			if (!event.isNull("url")) {
				builder.setUrl(event.getString("url"));
			}
			if (!event.isNull("rating")) {
				builder.setRating(event.getDouble("rating"));
			}
			if (!event.isNull("distance")) {
				builder.setDistance(event.getDouble("distance"));
			}

			builder.setCategories(getCategories(event));
			builder.setAddress(getAddress(event));
			builder.setImageUrl(getImageUrl(event));

			itemList.add(builder.build());
		}

		return itemList;
	}

	private void queryAPI(double lat, double lon) {
		List<Item> itemList = search(lat, lon, null);
		try {
			for (Item item : itemList) {
				JSONObject jsonObject = item.toJSONObject();
				System.out.println(jsonObject);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main entry for sample TicketMaster API requests.
	 */
	public static void main(String[] args) {
		TicketMasterAPI tmApi = new TicketMasterAPI();
		// Mountain View, CA
		// tmApi.queryAPI(37.38, -122.08);
		// London, UK // tmApi.queryAPI(51.503364, -0.12);
		// Houston, TX
		tmApi.queryAPI(29.682684, -95.295410);
	}
}