package org.springframework.cloud.sleuth.api;

import java.util.Map;

public interface BaggageManager {

	Map<String, String> getAllBaggage();

	Baggage getBaggage(String name);

	Baggage createBaggage(String name);

}
