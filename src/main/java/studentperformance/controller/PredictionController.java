package studentperformance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import studentperformance.dto.AiPredictionInsightsDto;
import studentperformance.dto.CalculatePredictionRequest;
import studentperformance.prediction.StudentPredictionResult;
import studentperformance.service.PredictionService;

@RestController
@RequestMapping("/api/prediction")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/{rollNo}")
    public ResponseEntity<StudentPredictionResult> getPrediction(@PathVariable("rollNo") String rollNo) {
        return ResponseEntity.ok(predictionService.getPrediction(rollNo));
    }

    @PostMapping("/{rollNo}/calculate")
    public ResponseEntity<StudentPredictionResult> calculateCustomPrediction(
            @PathVariable("rollNo") String rollNo,
            @RequestBody(required = false) CalculatePredictionRequest request) {
        return ResponseEntity.ok(predictionService.calculateCustomPrediction(rollNo, request));
    }

    @GetMapping("/{rollNo}/ai-insights")
    public ResponseEntity<AiPredictionInsightsDto> getAiInsights(@PathVariable("rollNo") String rollNo) {
        return ResponseEntity.ok(predictionService.getAiInsights(rollNo));
    }
}
