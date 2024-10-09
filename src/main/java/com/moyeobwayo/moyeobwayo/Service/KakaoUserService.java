package com.moyeobwayo.moyeobwayo.Service;
import java.io.BufferedReader;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;
import com.moyeobwayo.moyeobwayo.Domain.KakaoProfile;
import com.moyeobwayo.moyeobwayo.Domain.Party;
import com.moyeobwayo.moyeobwayo.Domain.UserEntity;
import com.moyeobwayo.moyeobwayo.Repository.KakaoProfileRepository;
import com.moyeobwayo.moyeobwayo.Repository.UserEntityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.ZoneId;

@Service
public class KakaoUserService {

    private final KakaoProfileRepository kakaoProfileRepository;
    private final UserEntityRepository userEntityRepository;

    public KakaoUserService(KakaoProfileRepository kakaoProfileRepository, UserEntityRepository userEntityRepository) {
        this.kakaoProfileRepository = kakaoProfileRepository;
        this.userEntityRepository = userEntityRepository;
    }
    @Value("${KAKAO_REST_KEY}")
    private String KAKAO_REST_KEY;

    //UserList중에 카카오 유저만 함수 호출
    public void sendKakaoCompletMesage(List<UserEntity> users, Party party, Date completeDate) {
        for (UserEntity user : users) {
            // 카카오 유저라면 메시지 보내기 (예: 카카오 API 호출)
            if (user.getKakaoProfile() != null) {
                sendCompleteMessage(user.getKakaoProfile(), party, completeDate);
            }
        }
    }
    //한 카카오 유저에게 메시지 전송
    public void sendCompleteMessage(KakaoProfile kakaoUser, Party party, Date completeDate) {
        try {
            // 1. JSON 템플릿 로드
            String template = loadJsonTemplate("src/main/resources/static/message_template.json");

            // 2. JSON 템플릿에서 값을 동적으로 대체
            String message = template.replace("{{party_title}}", party.getParty_name())
                    .replace("{{complete_time}}", formatDate(completeDate))
                    .replace("{{location}}", "미정");

            // 3. 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            headers.set("Authorization", "Bearer " + kakaoUser.getAccess_token());

            // 4. 요청 바디 설정
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("template_object", message);

            // 5. 요청 엔터티 생성 (헤더와 바디 포함)
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // 6. RestTemplate 생성
            RestTemplate restTemplate = new RestTemplate();

            // 7. API 호출 및 응답 받기
            String url = "https://kapi.kakao.com/v2/api/talk/memo/default/send";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            // 8. 응답 처리
            int statusCode = response.getStatusCodeValue();
            if (statusCode >= 200 && statusCode < 300) {
                System.out.println("Message sent successfully!");
                System.out.println("Response Body: " + response.getBody());
            } else if (statusCode == 401) {
                //권한 요청 로직 설정
                System.out.println("Error: 401 Unauthorized - Access token may be invalid or expired. Attempting to refresh the token.");
            } else if (statusCode == 403) {
                refreshKakaoAccToken(kakaoUser);
                Long targetID = kakaoUser.getKakaoUserId();
                Optional<KakaoProfile> newKakaoProfile = kakaoProfileRepository.findById(targetID);
                if (newKakaoProfile.isPresent()) {
                    if(kakaoUser.getAccess_token() == newKakaoProfile.get().getAccess_token()){
                        System.out.println("Kakao profile NOT updated!");
                    }else{
                        sendCompleteMessage(newKakaoProfile.get(), party, completeDate);
                    }
                }
                sendCompleteMessage(kakaoUser, party, completeDate);

                System.out.println("Error: 403 Forbidden - Access denied. Please check your permissions or the access token.");
            } else {
                System.out.println("Error: " + statusCode);
                System.out.println("Response Body: " + response.getBody());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // JSON 템플릿을 로드하는 함수
    private String loadJsonTemplate(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    private String formatDate(Date completeDate) {
        // Date -> LocalDateTime 변환
        LocalDateTime localDateTime = completeDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        // "MM월 dd일 HH시 mm분" 형식으로 포맷
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM월 dd일 HH시 mm분");
        return localDateTime.format(formatter);
    }

    public ResponseEntity<?> refreshKakaoAccToken(KakaoProfile kakaoProfile) {
        // 1. RestTemplate 생성
        RestTemplate restTemplate = new RestTemplate();

        // 2. 요청 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        // 3. 요청 바디 설정
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", KAKAO_REST_KEY); // 앱 REST API 키
        body.add("refresh_token", kakaoProfile.getRefresh_token());

        // 4. 요청 엔터티 생성 (헤더와 바디를 포함)
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        // 5. API 호출
        String url = "https://kauth.kakao.com/oauth/token";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        // 6. 응답 결과 처리
        if (response.getStatusCode().is2xxSuccessful()) {

            String newAccToekn = extractAccessTokenFromResponse(response.getBody());
            kakaoProfile.setAccess_token(newAccToekn);
            //DB에 반영
            try{
                kakaoProfileRepository.save(kakaoProfile)                ;
            }
            catch (Exception e){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
            kakaoProfileRepository.save(kakaoProfile);

            return ResponseEntity.ok(response.getBody());
        } else {
            System.out.println("Error: " + response.getStatusCode());
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        }
    }
    // refreshing 응답에서 acc토큰 추출함수
    private String extractAccessTokenFromResponse(String responseBody) {
        // JSON 파싱하여 access_token 추출 (간단한 구현)
        return responseBody.split("\"access_token\":\"")[1].split("\"")[0];
    }


    // 🌟 카카오 유저생성 및 조회로직
    public KakaoProfile createUser(String code) {
        // 1. 인가 코드로 액세스 토큰, 리프레시 토큰, 만료 시간 가져오기
        Map<String, Object> tokenInfo = getAccessTokenFromKakao(code);

        // 2. 액세스 토큰으로 사용자 정보 조회
        String accessToken = (String) tokenInfo.get("access_token");
        KakaoProfile kakaoProfile = getKakaoUserProfile(accessToken);

        // 3. 액세스 토큰 및 리프레시 토큰, 만료 시간 설정
        kakaoProfile.setAccess_token(accessToken);
        kakaoProfile.setRefresh_token((String) tokenInfo.get("refresh_token"));
        kakaoProfile.setExpires_in(convertToLong(tokenInfo.get("expires_in")));
        kakaoProfile.setRefresh_token_expires_in(convertToLong(tokenInfo.get("refresh_token_expires_in")));

        // 4. DB에 저장
        return kakaoProfileRepository.save(kakaoProfile);
    }

    // 인가 코드를 통해 액세스 토큰, 리프레시 토큰, 만료 시간 정보 가져오기
    private Map<String, Object> getAccessTokenFromKakao(String code) {
        String url = "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", KAKAO_REST_KEY); // 카카오 REST API 키
        params.add("redirect_uri", "http://127.0.0.1:3000/login/oauth/callback/kakao"); // 설정된 리다이렉트 URI
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();  // 전체 응답을 반환하여 필요한 값들을 추출
    }

    // 액세스 토큰으로 카카오 사용자 프로필 정보 조회
    private KakaoProfile getKakaoUserProfile(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> body = response.getBody();

        // 사용자 정보 추출 및 KakaoProfile 객체 생성
        Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        KakaoProfile kakaoProfile = new KakaoProfile();

        // ★ 여기서 id를 설정할 때, 정확하게 `longValue()`를 사용하여 변환합니다.
        if (body.get("id") instanceof Integer) {
            // 만약 `id` 값이 Integer일 경우 Long으로 명시적으로 변환
            kakaoProfile.setKakaoUserId(((Integer) body.get("id")).longValue());
        } else if (body.get("id") instanceof Long) {
            // 만약 `id` 값이 이미 Long 타입이라면 그대로 사용
            kakaoProfile.setKakaoUserId((Long) body.get("id"));
        } else {
            // 예상치 못한 타입일 경우 예외 처리
            throw new IllegalArgumentException("Unexpected ID type: " + body.get("id").getClass());
        }
        kakaoProfile.setNickname((String) profile.get("nickname"));
        kakaoProfile.setProfile_image((String) profile.get("profile_image_url"));

        return kakaoProfile;
    }

    private Long convertToLong(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue(); // Integer를 Long으로 변환
        } else if (value instanceof Long) {
            return (Long) value; // 이미 Long 타입이면 그대로 반환
        } else {
            throw new IllegalArgumentException("Cannot convert value to Long: " + value);
        }
    }

    // 🌟 새로운 linkUserToKakaoWithKakaoId 메서드
    public boolean linkUserToKakaoWithKakaoId(int currentUserId, int partyId, Long kakaoUserId) {
        // 1. 전달받은 currentUserId와 partyId로 UserEntity 조회
        Optional<UserEntity> userOptional = userEntityRepository.findByIdAndPartyId(currentUserId, partyId);
        if (userOptional.isEmpty()) {
            return false;  // 해당 UserEntity가 존재하지 않으면 연결 불가
        }

        UserEntity userEntity = userOptional.get();

        // 2. DB에서 전달받은 kakao_user_id로 KakaoProfile 조회
        Optional<KakaoProfile> kakaoProfileOptional = kakaoProfileRepository.findById(kakaoUserId);
        if (kakaoProfileOptional.isEmpty()) {
            return false;  // 해당 KakaoProfile이 없으면 연결 불가
        }

        KakaoProfile kakaoProfile = kakaoProfileOptional.get();

        // 3. UserEntity에 KakaoProfile 연결
        userEntity.setKakaoProfile(kakaoProfile);

        // 4. DB에 UserEntity 저장
        userEntityRepository.save(userEntity);

        return true;
    }
}



