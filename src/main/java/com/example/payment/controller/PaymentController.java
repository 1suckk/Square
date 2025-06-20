package com.example.payment.controller;

import com.example.classes.dto.ClassResponse;
import com.example.classes.entity.ClassesEntity;
import com.example.classes.jpa.ClassesRepository;
import com.example.payment.dto.PaymentEnrollDto;
import com.example.payment.dto.PaymentEnrollOutputDto;
import com.example.payment.dto.PaymentGetChildrenDto;
import com.example.payment.entity.EnrollEntity;
import com.example.payment.jpa.PaymentEnrollRepository;
import com.example.payment.service.PaymentService;
import com.example.user.entity.StudentsEntity;

import lombok.RequiredArgsConstructor;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@CrossOrigin
/*
이건 원장의 결제 관리 창에서 반 조회를 위해 만든 레포지토리입니다
차후 결제 기능을 위해 다양한 내용들이 만들어질 예정입니다
*/
public class PaymentController {
    private final ClassesRepository classesRepository;
    private final PaymentService paymentService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass()); //학부모 결제 파트
    private final PaymentEnrollRepository paymentEnrollRepository;

    @GetMapping("/dir/{academyId}/payment/getclass")
    public ResponseEntity<List<ClassResponse>> getPaymentClass(@PathVariable int academyId) {
        List<ClassesEntity> classes = classesRepository.findByAcademyId(academyId);

        List<ClassResponse> response = classes.stream()
                .map(c -> ClassResponse.builder()
                        .id(c.getClassId())
                        .name(c.getName())
                        .capacity(c.getCapacity())
                        .teacherId(c.getTeacher().getTeacherId())
                        .teacherName(c.getTeacher().getUser().getName())
                        .tuition(c.getTuition() != null ? c.getTuition() : 0)
                        .build()).toList();

        return ResponseEntity.ok(response);
    }

    //수업료 수정하는 부분
    @PostMapping("/dir/{classId}/payment/UpdateTuition")
    public ResponseEntity<?> updateTuition(@PathVariable int classId,
                                          @RequestBody Map<String, Object> payload)
    /*
    payload 는 { "tuition": 50000 } 과 같이 json body 로 받아오는데
    여기서 Map<String, Object> 사용함.
    */
    {
        int tuition = Integer.parseInt(payload.get("tuition").toString());

        paymentService.updateTuition(classId, tuition);
        return ResponseEntity.ok().build();
    }
    
    /*
    학부모가 결제한 이력을 조회하는 부분
    학원 id와 학부모 id로 매핑을 잡아서 특정 학원에 자녀를 둔 학부모의 결제 내역을 확인하도록 한다
    결제 과정을 디자인 과정에서보다 수정할 필요 있음
    학원 내 모든 클래스 정보 불러오기
    */
    @GetMapping("/parent/{academyId}/classes")
    public ResponseEntity<List<ClassResponse>> getClassesByAcademy(@PathVariable int academyId) {
        List<ClassesEntity> classes = classesRepository.findByAcademyId(academyId);

        List<ClassResponse> response = classes.stream()
                .map(c -> ClassResponse.builder()
                        .id(c.getClassId())
                        .name(c.getName())
                        .capacity(c.getCapacity())
                        .teacherId(c.getTeacher().getTeacherId())
                        .teacherName(c.getTeacher().getUser().getName())
                        .tuition(c.getTuition() != null ? c.getTuition() : 0)
                        .build()).toList();
        return ResponseEntity.ok(response);
    }

    /*
    학부모가 수업을 선택하면 그 아래에 자녀를 선택하게 하기
    새로만든 dto 를 통해 자녀 이름만 받아올 수 있게 구현
    */
    @GetMapping("/parent/{roleId}/students")
    public ResponseEntity<List<PaymentGetChildrenDto>> getStudentsByParentId(@PathVariable int roleId) {
        List<StudentsEntity> students = paymentService.getChildrenByParentId(roleId);

        /*
        JSON 무한루프를 피하기 위해서 Dto 를 통해 2차적으로 response 로 받아와서 GET
        자녀가 여러 명인 경우를 대비해 map()으로 반복적으로 묶어준다
        */
        List<PaymentGetChildrenDto> response = students.stream()
                .map(s -> new PaymentGetChildrenDto(s.getStudentId(),
                        s.getUser().getName())) //테이블의 id 값과 이름을 반복적으로 받아온다.
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    //학부모가 장바구니에 넣으면 enroll에 등록이 되어야 함
    @PostMapping("/parent/paymentEnroll/{academyId}/{parentId}/{studentId}")
    public ResponseEntity<?> registerClassEnrollment(@RequestBody PaymentEnrollDto dto,
                                                     @PathVariable int academyId,
                                                     @PathVariable int parentId,
                                                     @PathVariable int studentId)
    {
        PaymentEnrollDto result = paymentService.insertEnrollClass(academyId, parentId, studentId, dto);
        return ResponseEntity.ok(result);
    }

    /*
    enroll db에 입력한 내용 중 프론트에서 라우팅된 roleId와 학부모 id와 동일한 부분을
    where 절로 받아와서 결제할 수 있게 할 것
    @Getmapping (원장 / isPay / 학원 id ) 학부모 장바구니 창은 어차피 학부모 id만 받아오면 됨
    */

    @GetMapping("/dir/{academyId}/enrollList")
    public ResponseEntity<List<PaymentEnrollOutputDto>> getDirEnrollList(@PathVariable int academyId) {
        List<EnrollEntity> allEnroll = paymentService.getDirEnrollList(academyId);

        List<PaymentEnrollOutputDto> response = allEnroll.stream()
                .map(e -> new PaymentEnrollOutputDto(
                        e.getEnrollId(),
                        e.getParent().getUser().getName(),
                        e.getStudent().getUser().getName(),
                        e.getClasses().getTeacher().getUser().getName(),
                        e.getClasses().getName(),
                        e.getDuration(),
                        e.getClasses().getTuition(),
                        e.getIsPay()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /*
    enroll db에 입력한 내용 중 프론트에서 라우팅된 roleId와 학부모 id와 동일한 부분을
    where 절로 받아와서 결제할 수 있게 할 것
    @Getmapping (학부모 / enroll / 학부모 id ) 학부모 장바구니 창은 어차피 학부모 id만 받아오면 됨
    */
    @GetMapping("/parent/{parentId}/enrollList")
    public ResponseEntity<List<PaymentEnrollOutputDto>> getEnrollListByParentId(@PathVariable int parentId) {
        List<EnrollEntity> enrollList = paymentService.getEnrollByParentId(parentId);

        /*
        JSON 무한루프를 피하기 위해서 Dto 를 통해 2차적으로 response 로 받아와서 GET
        자녀가 여러 명인 경우를 대비해 map()으로 반복적으로 묶어준다
        EnrollEntity -->
        PaymentEnrollRepository(select 문) -->
        PaymentEnrollOutputDto 으로 묶어준다

        1. 장바구니의 일련번호
        2. 결제할 학부모의 이름
        3. 자녀 학생의 이름
        4. 클래스 이름
        5. 수업 기간
        6. 클래스 수업료

        를 통해서 학부가 결제하기 전 확인해야 할 정보를 매핑한다.
        */

        List<PaymentEnrollOutputDto> response = enrollList.stream()
                .map(e -> new PaymentEnrollOutputDto(
                    e.getEnrollId(),
                    e.getParent().getUser().getName(),
                    e.getStudent().getUser().getName(),
                    e.getClasses().getTeacher().getUser().getName(),
                    e.getClasses().getName(),
                    e.getDuration(),
                    e.getClasses().getTuition(),
                    e.getIsPay()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/{studentId}/enrollList")
    public ResponseEntity<List<PaymentEnrollOutputDto>> getEnrollListByParentIdForStu(@PathVariable int studentId) {
        List<EnrollEntity> enrollList = paymentService.getEnrollByParentIdForStu(studentId);

        /*
        JSON 무한루프를 피하기 위해서 Dto 를 통해 2차적으로 response 로 받아와서 GET
        자녀가 여러 명인 경우를 대비해 map()으로 반복적으로 묶어준다
        EnrollEntity -->
        PaymentEnrollRepository(select 문) -->
        PaymentEnrollOutputDto 으로 묶어준다

        1. 장바구니의 일련번호
        2. 결제할 학부모의 이름
        3. 자녀 학생의 이름
        4. 클래스 이름
        5. 수업 기간
        6. 클래스 수업료

        를 통해서 학부가 결제하기 전 확인해야 할 정보를 매핑한다.
        */

        List<PaymentEnrollOutputDto> response = enrollList.stream()
                .map(e -> new PaymentEnrollOutputDto(
                        e.getEnrollId(),
                        e.getParent().getUser().getName(),
                        e.getStudent().getUser().getName(),
                        e.getClasses().getTeacher().getUser().getName(),
                        e.getClasses().getName(),
                        e.getDuration(),
                        e.getClasses().getTuition(),
                        e.getIsPay()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    //토스페이먼츠로 장바구니 금액만큼 결제
    @RequestMapping(value = "/parent/confirm")
    public ResponseEntity<JSONObject> confirmPayment(@RequestBody String jsonBody) throws Exception {
        JSONParser parser = new JSONParser();
        //결제에 필요한 주문id, 결제금액, 클라이언트 키
        String orderId;
        String amount;
        String paymentKey;
        Long enrollId = null; //이 위치에 놓을 것 (수정 금지)

        try {
            // 클라이언트에서 받은 JSON 요청 바디입니다.
            JSONObject requestData = (JSONObject) parser.parse(jsonBody);
            paymentKey = (String) requestData.get("paymentKey");
            orderId = (String) requestData.get("orderId");
            amount = (String) requestData.get("amount");
            //enrollId 추가
            if (requestData.get("enrollId") != null) {
                enrollId = Long.parseLong(requestData.get("enrollId").toString());
            }
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        };

        JSONObject obj = new JSONObject();
        obj.put("orderId", orderId);
        obj.put("amount", amount);
        obj.put("paymentKey", paymentKey);

        // 토스페이먼츠 API는 시크릿 키를 사용자 ID로 사용하고, 비밀번호는 사용하지 않습니다.
        // 비밀번호가 없다는 것을 알리기 위해 시크릿 키 뒤에 콜론을 추가합니다.
        String widgetSecretKey = "test_sk_DLJOpm5QrlLNgNNRkngPrPNdxbWn";
        Base64.Encoder encoder = Base64.getEncoder();
        byte[] encodedBytes = encoder.encode((widgetSecretKey + ":").getBytes(StandardCharsets.UTF_8));
        String authorizations = "Basic " + new String(encodedBytes);

        // 결제를 승인하면 결제수단에서 금액이 차감돼요.
        URL url = new URL("https://api.tosspayments.com/v1/payments/confirm");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Authorization", authorizations);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(obj.toString().getBytes("UTF-8"));
        //성공 코드 지정 (200)
        int code = connection.getResponseCode();
        boolean isSuccess = code == 200;
        InputStream responseStream = isSuccess ? connection.getInputStream() : connection.getErrorStream();

        if (isSuccess && enrollId != null) {
            paymentService.updateEnrollIsPay(enrollId, "T");
        }

        // 결제 성공 및 실패 비즈니스 로직을 구현하세요.
        Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
        JSONObject jsonObject = (JSONObject) parser.parse(reader);
        responseStream.close();

        return ResponseEntity.status(code).body(jsonObject);
    }


    //장바구니 단일 제거
    @DeleteMapping("/parent/deleteEnrollList/{enrollId}")
    public ResponseEntity<?> removeEnrollList(@PathVariable int enrollId) {
        if(!paymentEnrollRepository.existsById(enrollId)) {
          return ResponseEntity.notFound().build();
        }

        paymentEnrollRepository.deleteById(enrollId);
        return ResponseEntity.ok().build();
    }

    //학부모가 이미 결제한 내역
    @GetMapping("/parent/{parentId}/PrevPay")
    public ResponseEntity<List<PaymentEnrollOutputDto>> getPrevPayList(@PathVariable int parentId) {
        List<EnrollEntity> prevpay = paymentService.getPrevPayByParentId(parentId);

        List<PaymentEnrollOutputDto> response = prevpay.stream()
                .map(e->new PaymentEnrollOutputDto(
                        e.getEnrollId(),
                        e.getParent().getUser().getName(),
                        e.getStudent().getUser().getName(),
                        e.getClasses().getTeacher().getUser().getName(),
                        e.getClasses().getName(),
                        e.getDuration(),
                        e.getClasses().getTuition(),
                        e.getIsPay()
                )).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    //학생 페이지에서 보는 학부모가 이미 결제한 내역
    @GetMapping("/student/{studentId}/PrevPay")
    public ResponseEntity<List<PaymentEnrollOutputDto>> getPrevPayListForStu(@PathVariable int studentId) {
        List<EnrollEntity> prevpay = paymentService.getPrevPayByParentIdForStu(studentId);

        List<PaymentEnrollOutputDto> response = prevpay.stream()
                .map(e->new PaymentEnrollOutputDto(
                        e.getEnrollId(),
                        e.getParent().getUser().getName(),
                        e.getStudent().getUser().getName(),
                        e.getClasses().getTeacher().getUser().getName(),
                        e.getClasses().getName(),
                        e.getDuration(),
                        e.getClasses().getTuition(),
                        e.getIsPay()
                )).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}