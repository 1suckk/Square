package com.example.payment.service;

import com.example.classes.entity.ClassesEntity;
import com.example.classes.jpa.ClassesRepository;
import com.example.payment.dto.PaymentEnrollDto;
import com.example.payment.entity.EnrollEntity;
import com.example.payment.jpa.PaymentChildrenRepository;
import com.example.payment.jpa.PaymentEnrollRepository;
import com.example.payment.jpa.PaymentGetClassRepository;
import com.example.user.entity.ParentsEntity;
import com.example.user.entity.StudentsEntity;
import com.example.user.jpa.ParentsRepository;
import com.example.user.jpa.StudentsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentGetClassRepository paymentGetClassRepository;
    private final PaymentChildrenRepository paymentChildrenRepository;
    private final PaymentEnrollRepository paymentEnrollRepository;
    private final ParentsRepository parentsRepository;
    private final StudentsRepository studentsRepository;
    private final ClassesRepository classesRepository;

    @Transactional
    public void updateTuition(int classId, int tuition) {
        paymentGetClassRepository.updateTuitionByClassId(classId, tuition);
    }
    
    //학부모가 자녀 수강신청 할때 자신의 자녀를 선택하는 과정
    @Transactional
    public List<StudentsEntity> getChildrenByParentId(int parentId) {
        return paymentChildrenRepository.getChildrenByParentId(parentId);
    }

       /* 장바구니에 해당하는 enroll DB 에 insert 하는 방법
        jpql 에서는 EntityManager 를 활용해서 insert 를 구현하기 때문에
        save 를 사용한다. */
        @Transactional
        public PaymentEnrollDto insertEnrollClass(int academyId, int parentId, int studentId, PaymentEnrollDto dto) {
            ParentsEntity parent = parentsRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent not found"));
            StudentsEntity student = studentsRepository.findById(dto.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            ClassesEntity classes = classesRepository.findById(dto.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));

            EnrollEntity enroll = EnrollEntity.builder()
                    .enrollId(dto.getEnrollId())
                    .parent(parent)
                    .student(student)
                    .classes(classes)
                    .duration(dto.getDuration())
                    .academyId(classes.getAcademy().getAcademyId())
                    .isPay("F")
                    .build();

            EnrollEntity saved = paymentEnrollRepository.save(enroll);

            return PaymentEnrollDto.builder()
                    .enrollId(saved.getEnrollId())
                    .parentId(parentId)
                    .studentId(studentId)
                    .classId(dto.getClassId())
                    .duration(dto.getDuration())
                    .isPay(saved.getIsPay()) //렌더링에 필요한 값? 미정
                    .build();
        }

    //원장이 수강신청한 학부모와 학생의 정보를 확인
    @Transactional
    public List<EnrollEntity> getDirEnrollList(int academyId) {
        return paymentEnrollRepository.getAllEnroll(academyId);
    }

    //학부모가 장바구니에 수업을 신청하고 자신의 학부모 id를 이용해서 결제해야 할 부분을 select
    @Transactional
    public List<EnrollEntity> getEnrollByParentId(int parentId) {
        return paymentEnrollRepository.getEnrollByParentId(parentId);
    }

    @Transactional
    public List<EnrollEntity> getEnrollByParentIdForStu(int studentId) {
        return paymentEnrollRepository.getEnrollByParentIdForStu(studentId);
    }

    //결제 성공 후 isPay T로 수정
    @Transactional
    public void updateEnrollIsPay(Long enrollId, String isPay) {
        paymentEnrollRepository.updateIsPay(enrollId, isPay);
    }
    
    //학부모의 기존에 결제한 내역을 받아오는 부분
    @Transactional
    public List<EnrollEntity> getPrevPayByParentId(int parentId) {
        return paymentEnrollRepository.getPrevPayByParentId(parentId);
    }

    @Transactional
    public List<EnrollEntity> getPrevPayByParentIdForStu(int studentId) {
        return paymentEnrollRepository.getPrevPayByParentIdForStu(studentId);
    }

}
