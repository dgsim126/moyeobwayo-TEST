package com.moyeobwayo.moyeobwayo.Controller;

import com.moyeobwayo.moyeobwayo.Domain.Timeslot;
import com.moyeobwayo.moyeobwayo.Domain.dto.TimeslotRequestDTO;
import com.moyeobwayo.moyeobwayo.Domain.dto.TimeslotResponseDTO;
import com.moyeobwayo.moyeobwayo.Service.TimeslotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/timeslots")
@RequiredArgsConstructor
public class TimeslotController {

    private final TimeslotService timeslotService;

    // 같은 party_id를 가진 타임슬롯 조회 (특정 파티에 속한 타임슬롯)
    // [GET] /api/v1/timeslots/party/{party_id}
    @GetMapping("/party/{party_id}")
    public ResponseEntity<List<TimeslotResponseDTO>> getTimeslotsByPartyId(@PathVariable String party_id) {
        List<TimeslotResponseDTO> timeslots = timeslotService.getTimeslotsByPartyId(party_id);
        return ResponseEntity.ok(timeslots);
    }

    // 타임슬롯 생성 (유저가 날짜 투표)
    // [POST] /api/v1/timeslots
    @PostMapping
    public ResponseEntity<?> createTimeslot(@RequestBody TimeslotRequestDTO timeslotRequestDTO) {
        try{
            TimeslotResponseDTO response = timeslotService.createTimeslot(timeslotRequestDTO);
            return ResponseEntity.status(201).body(response);

        }catch (IllegalArgumentException e){
            System.out.println(e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 타임슬롯 수정 (날짜 투표 수정)
    // [PUT] /api/v1/timeslots/{timeslot_id}
    //@PutMapping("/{timeslot_id}")
    //public ResponseEntity<TimeslotResponseDTO> updateTimeslot(
    //        @PathVariable Long timeslot_id,
    //        @RequestBody TimeslotRequestDTO timeslotRequestDTO) {
    //
    //    TimeslotResponseDTO response = timeslotService.updateTimeslot(
    //            timeslot_id,
    //            timeslotRequestDTO.getSelectedStartTime(),
    //            timeslotRequestDTO.getSelectedEndTime()
    //    );
    //
    //    return ResponseEntity.ok(response);
    //}

    // 타임슬롯 삭제 (날짜 투표 삭제)
    // [DELETE] /api/v1/timeslots/{timeslot_id}
    @DeleteMapping("/{timeslot_id}")
    public ResponseEntity<Void> deleteTimeslot(
            @PathVariable Long timeslot_id,
            @RequestParam("userId") Long userId, // 쿼리 파라미터로 kakaoId 받음
            @RequestParam("partyId") String partyId  // 쿼리 파라미터로 partyId 받음
    ) {
        timeslotService.deleteTimeslot(timeslot_id, userId, partyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/timeslots/user-timeslots
     * 특정 user, party id에 해당하는 개체의 모든 날짜별 타임슬롯 정보를 가져오기
     */
    @PostMapping("/user-timeslots")
    public ResponseEntity<Map<String, Object>> getTimeslotsByUserAndParty(
            @RequestBody Map<String, Object> request) {

        Long userId = Long.parseLong(request.get("userId").toString());
        String partyId = request.get("partyId").toString();

        Map<String, Object> result = timeslotService.getTimeslotsByUserAndParty(userId, partyId);
        return ResponseEntity.ok(result);
    }

}
