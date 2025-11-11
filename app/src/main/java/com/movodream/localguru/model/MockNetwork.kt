package com.movodream.localguru.model


object MockNetwork {
    // RETURN schema as STRING like you'd get from API. Replace with real network call.
    fun fetchSchemaString(): String {
        return """
        {
          "form_id": "site_documentation_v1",
          "title": "Golden Temple Documentation",
          "progress": 40,
          "tags": ["Religious Site"],
          "tabs": [
            {"id":"basic_info","title":"Basic Info","order":1,"fields":[
                {"id":"site_name","type":"text","label":"Site Name","placeholder":"Golden Temple (Sri Harmandir Sahib)","required":true,"minLength":3,"maxLength":120},
                {"id":"type","type":"select","label":"Type","required":true,"options":[{"value":"religious","label":"Religious Site"},{"value":"heritage","label":"Heritage"}]},
                {"id":"address","type":"textarea","label":"Address","required":true,"maxLength":250},
                {"id":"gps_latitude","type":"number","label":"GPS Latitude","required":true,"min":-90,"max":90,"precision":6},
                {"id":"gps_longitude","type":"number","label":"GPS Longitude","required":true,"min":-180,"max":180,"precision":6},
              
            ]},
            {"id":"details","title":"Details","order":2,"fields":[
                {"id":"opening_hours","type":"text","label":"Opening Hours"},
                {"id":"entry_fee","type":"select","label":"Entry Fee","options":[{"value":"free","label":"Free"},{"value":"paid","label":"Paid"}]},
                {"id":"best_time","type":"textarea","label":"Best Time to Visit"}
            ]},
            {"id":"photos","title":"Photos","order":3,"fields":[
                {"id":"photo_requirements","type":"checkbox_group","label":"Photo Requirements","options":[{"value":"exterior","label":"Exterior Shots"},{"value":"interior","label":"Interior Shots"},{"value":"architecture","label":"Architectural Details"},{"value":"surroundings","label":"Surroundings"}],"minSelected":1},
                {"id":"photos_upload","type":"photo_list","label":"Upload Photos","required":true,"minItems":5,"maxItems":20,"instructions":"Minimum 5 photos required"}
            ]},
            {"id":"review","title":"Review","order":4,"fields":[
                {"id":"final_notes","type":"textarea","label":"Final Notes & Observations"},
                {"id":"data_confidence","type":"select","label":"Data Quality Confidence","required":true,"options":[{"value":"high","label":"High - Verified"},{"value":"medium","label":"Medium - Some Uncertainty"},{"value":"low","label":"Low - Unverified"}],"default":"medium"},
                {"id":"verification_checklist","type":"checkbox_group","label":"Verification Checklist","options":[{"value":"gps_verified","label":"GPS coordinates verified"},{"value":"contact_confirmed","label":"Contact information confirmed"},{"value":"hours_crosschecked","label":"Operating hours cross-checked"},{"value":"photos_clear","label":"Photos are clear and representative"}]}
            ] }
          ],
          "submit_button": {"label":"Submit Data","draft_label":"Save Draft","endpoint":"/api/v1/site_documentation/submit","method":"POST"}
        }
        """.trimIndent()
    }

    fun fetchSchemaString2(): String {
        return """
    {
      "form_id": "restaurant_data_v1",
      "title": "Kesar Da Dhaba Data Collection",
      "progress": 0,
      "tags": ["Restaurant"],
      "tabs": [
        {
          "id": "basic_info",
          "title": "Basic Info",
          "order": 1,
          "fields": [
            {
              "id": "restaurant_name",
              "type": "text",
              "label": "Restaurant Name",
              "required": true,
              "minLength": 2,
              "maxLength": 120
            },
            {
              "id": "type",
              "type": "select",
              "label": "Type",
              "required": true,
              "options": [
                {"value": "dhaba", "label": "Dhaba"},
                {"value": "restaurant", "label": "Restaurant"}
              ]
            },
            {
              "id": "cuisine_type",
              "type": "select",
              "label": "Cuisine Type",
              "required": true,
              "options": [
                {"value": "punjabi", "label": "Punjabi"},
                {"value": "north", "label": "North Indian"},
                {"value": "multi", "label": "Multi"}
              ]
            },
            {
              "id": "address",
              "type": "text",
              "label": "Address",
              "required": true
            }
          ]
        },
        {
          "id": "menu_pricing",
          "title": "Menu & Pricing",
          "order": 2,
          "fields": [
            {
              "id": "signature_dishes",
              "type": "textarea",
              "label": "Signature Dishes",
              "required": true,
              "maxLength": 1000
            },
            {
              "id": "must_try",
              "type": "text",
              "label": "Must-Try Item",
              "required": false
            },
            {
              "id": "price_range_for_two",
              "type": "text",
              "label": "Price Range for Two",
              "required": false
            },
            {
              "id": "avg_meal_cost",
              "type": "text",
              "label": "Average Meal Cost",
              "required": false
            },
            {
              "id": "food_type",
              "type": "checkbox_group",
              "label": "Food Type",
              "options": [
                {"value": "pure_veg", "label": "Pure Vegetarian"},
                {"value": "jain_options", "label": "Jain Options"},
                {"value": "spicy", "label": "Spicy Food"},
                {"value": "mild", "label": "Mild Options"}
              ]
            }
          ]
        },
        {
          "id": "atmosphere",
          "title": "Atmosphere",
          "order": 3,
          "fields": [
            {
              "id": "atmosphere_experience",
              "type": "checkbox_group",
              "label": "Atmosphere & Experience",
              "options": [
                {"value": "bustling", "label": "Bustling"},
                {"value": "traditional", "label": "Traditional"},
                {"value": "family_friendly", "label": "Family-friendly"},
                {"value": "quick_service", "label": "Quick Service"}
              ],
              "required": false
            },
            {
              "id": "seating_capacity",
              "type": "text",
              "label": "Seating Capacity",
              "required": false
            },
            {
              "id": "operating_hours",
              "type": "text",
              "label": "Operating Hours",
              "required": false
            },
            {
              "id": "peak_hours",
              "type": "text",
              "label": "Peak Hours",
              "required": false
            },
            {
              "id": "hygiene",
              "type": "select",
              "label": "Hygiene Observation",
              "options": [
                {"value": "good", "label": "Good"},
                {"value": "average", "label": "Average"},
                {"value": "poor", "label": "Poor"}
              ]
            },
            {
              "id": "rating",
              "type": "rating",
              "label": "Overall Rating",
              "max": 5,
              "required": false
            }
          ]
        },
        {
          "id": "photos",
          "title": "Photos",
          "order": 4,
          "fields": [
          {
              "id": "interior_shots",
              "type": "checkbox_group",
              "label": "Photo Requirements",
              "options": [
                {"value": "interior", "label": "Interior Shots"},
                {"value": "exterior", "label": "Exterior Shots"}
              ]
            },
            {
              "id": "photos_upload",
              "type": "photo_list",
              "label": "Upload Photos",
              "required": true,
              "minItems": 3,
              "maxItems": 15
            }
           
          ]
        },
        {
          "id": "review",
          "title": "Review",
          "order": 5,
          "fields": [
            {
              "id": "final_notes",
              "type": "textarea",
              "label": "Final Notes",
              "required": false,
              "maxLength": 2000
            }
          ]
        }
      ],
      "submit_button": {
        "label": "Submit Data",
        "draft_label": "Save Draft",
        "endpoint": "/api/v1/restaurant_data/submit",
        "method": "POST"
      }
    }
    """.trimIndent()
    }

}
