package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {

    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity){
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswer(prompt);
        log.info("AI Response for activity {}: {}", activity.getId(), aiResponse);
        return processAiResponse(activity, aiResponse);
    }

    private Recommendation processAiResponse(Activity activity, String aiResponse) {
        // Here you can parse the AI response and save it to the database or perform other actions
        // For example, you might want to convert the JSON response into a Recommendation object
        // and save it using a RecommendationRepository (not shown here)
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(aiResponse);
            JsonNode textNode = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            String jsonContent = textNode.asText()
                    .replaceAll("```json\\n", "")
                    .replaceAll("\\n```", "")
                    .trim();

//            log.info("Extracted JSON content: {}", jsonContent);

            JsonNode analysisNode = objectMapper.readTree(jsonContent);
            JsonNode analysis = analysisNode.path("analysis");
            StringBuilder analysisSummary = new StringBuilder();
            addAnalysisSection(analysisSummary, analysis, "overall", "Overall Analysis:");
            addAnalysisSection(analysisSummary, analysis, "pace", "Pace:");
            addAnalysisSection(analysisSummary, analysis, "heartRate", "Heart Rate:");
            addAnalysisSection(analysisSummary, analysis, "caloriesBurned", "Calories:");

            List<String> improvements = extractImprovements(analysisNode.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisNode.path("suggestions"));
            List<String> safety = extractSafety(analysisNode.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendation(analysisSummary.toString().trim())
                    .improvementAreas(improvements)
                    .suggestedActivities(suggestions)
                    .safety(safety)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

        }catch (Exception e){
            log.error("Failed to process AI response for activity {}: {}", activity.getId(), e.getMessage());
            return createDefaultRecommendation(activity);
        }
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation("No specific recommendations available.")
                .improvementAreas(Collections.singletonList("Continue with your current routine."))
                .suggestedActivities(Collections.singletonList("No specific workout suggestions provided."))
                .safety(Collections.singletonList("No specific safety guidelines provided."))
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    private List<String> extractSafety(JsonNode safety) {
        List<String> safetyPoints = new ArrayList<>();
        if(safety.isArray()){
            safety.forEach(point -> safetyPoints.add(point.asText()));
        }
        return safetyPoints.isEmpty() ? Collections.singletonList("No specific safety guidelines provided.") : safetyPoints;
    }

    private List<String> extractSuggestions(JsonNode suggestions) {
        List<String> suggestionList = new ArrayList<>();
        if(suggestions.isArray()){
            suggestions.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestionList.add(String.format("%s: %s", workout, description));
            });
        }
        return suggestionList.isEmpty() ? Collections.singletonList("No specific workout suggestions provided.") : suggestionList;
    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        List<String> improvements = new ArrayList<>();
        if(improvementsNode.isArray()){
            improvementsNode.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String recommendation = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s", area, recommendation));
            });

        }
        return improvements.isEmpty() ? Collections.singletonList("No specific improvements suggested.") : improvements;    }

    private void addAnalysisSection(StringBuilder analysisSummary, JsonNode analysis, String key, String prefix) {
        if(!analysis.path(key).isMissingNode()){
            analysisSummary.append(prefix).append(" ").append(analysis.path(key).asText()).append("\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        return String.format("""
        Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format:
        {
          "analysis": {
            "overall": "Overall analysis here",
            "pace": "Pace analysis here",
            "heartRate": "Heart rate analysis here",
            "caloriesBurned": "Calories analysis here"
          },
          "improvements": [
            {
              "area": "Area name",
              "recommendation": "Detailed recommendation"
            }
          ],
          "suggestions": [
            {
              "workout": "Workout name",
              "description": "Detailed workout description"
            }
          ],
          "safety": [
            "Safety point 1",
            "Safety point 2"
          ]
        }

        Analyze this activity:
        Activity Type: %s
        Duration: %d minutes
        Calories Burned: %d
        Additional Metrics: %s
        
        Provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines.
        Ensure the response follows the EXACT JSON format shown above.
        """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics()
        );

    }
}
