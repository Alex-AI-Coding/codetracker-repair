package com.io.kira.application.chatbot.service;

import com.io.kira.application.activity.port.out.ActivityAppRepository;
import com.io.kira.application.activity.port.out.StudentActivityInfoAppRepository;
import com.io.kira.application.announcement.port.out.AnnouncementAppRepository;
import com.io.kira.application.activity.result.StudentActivityOverviewData;
import com.io.kira.application.activity.result.StudentSummaryData;
import com.io.kira.application.activity.result.StudentSubmissionDetailsData;
import com.io.kira.application.classroom.port.out.ClassroomAppRepository;
import com.io.kira.application.classroom.port.out.ClassroomStudentAppRepository;
import com.io.kira.domain.activity.entity.Activity;
import com.io.kira.domain.activity.valueObject.ActivityStatus;
import com.io.kira.domain.activity.valueObject.SubmissionStatus;
import com.io.kira.domain.classroom.aggregate.ClassroomAggregate;
import com.io.kira.domain.classroom.entity.Classroom;
import com.io.kira.domain.classroom.entity.ClassroomSettings;
import com.io.kira.domain.classroom.entity.ClassroomStudent;
import com.io.kira.domain.announcement.entity.Announcement;
import com.io.kira.domain.classroom.valueObject.ClassroomStatus;
import com.io.kira.domain.github.valueobject.GithubSubmissionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

class ChatbotContextServiceTest {

    private StudentActivityInfoAppRepository studentActivityInfoRepository;
    private ActivityAppRepository activityRepository;
    private ClassroomAppRepository classroomRepository;
    private ClassroomStudentAppRepository classroomStudentRepository;
    private ChatbotContextService service;
    private AnnouncementAppRepository announcementRepository;
    private ChatbotAccessService accessService;

    @BeforeEach
    void setUp() {
        studentActivityInfoRepository = mock(StudentActivityInfoAppRepository.class);
        activityRepository = mock(ActivityAppRepository.class);
        classroomRepository = mock(ClassroomAppRepository.class);
        classroomStudentRepository = mock(ClassroomStudentAppRepository.class);
        announcementRepository = mock(AnnouncementAppRepository.class);
        accessService = mock(ChatbotAccessService.class);
        service = new ChatbotContextService(
                studentActivityInfoRepository,
                activityRepository,
                classroomRepository,
                classroomStudentRepository,
                announcementRepository,
                accessService
        );
    }

    @Test
    void studentContextUsesOnlyTheCurrentStudentsOwnActivityView() {
        UUID userId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        when(accessService.getAccess(userId, classroomId)).thenReturn(ChatbotAccessService.ClassroomAccess.STUDENT);
        Instant now = Instant.now();

        Activity activity = Activity.reconstitute(
                activityId,
                classroomId,
                instructorId,
                "REST API Project",
                "Build the project",
                now.plusSeconds(86_400),
                ActivityStatus.PUBLISHED,
                100,
                now,
                now
        );

        StudentActivityOverviewData ownActivity = new StudentActivityOverviewData(
                activityId,
                activity.getTitle(),
                activity.getDescription(),
                activity.getMaxScore(),
                activity.getDueDate(),
                UUID.randomUUID(),
                "student-rest-api",
                "https://github.com/student/rest-api",
                GithubSubmissionMode.EXISTING,
                now,
                "abc123",
                SubmissionStatus.GRADED,
                92,
                "Good work",
                ActivityStatus.PUBLISHED
        );

        Classroom classroom = new Classroom(
                classroomId,
                instructorId,
                "Backend Development",
                "Backend classroom",
                "BACK01",
                ClassroomStatus.ACTIVE,
                now,
                now
        );

        when(activityRepository.findActivitiesByClassroomId(classroomId))
                .thenReturn(List.of(activity));
        when(activityRepository.findStudentActivities(classroomId, userId))
                .thenReturn(List.of(ownActivity));
        when(classroomRepository.findByClassroomId(classroomId))
                .thenReturn(Optional.of(new ClassroomAggregate(
                        classroom,
                        new ClassroomSettings(classroomId, false, null, 30)
                )));

        String context = service.buildClassroomContext(
                userId,
                classroomId,
                ChatbotAccessService.ClassroomAccess.STUDENT
        );

        assertTrue(context.contains("student-rest-api"));
        assertTrue(context.contains("Score: 92"));
        assertTrue(context.contains("Feedback: Good work"));
        assertFalse(context.contains("STUDENT ACTIVITY PROGRESS"));
        verifyNoInteractions(studentActivityInfoRepository);
    }

    @Test
    void studentCannotSeeDraftInstructionsOrAnotherStudentsAcademicData() {
        UUID user = UUID.randomUUID(), classroom = UUID.randomUUID();
        when(accessService.getAccess(user, classroom)).thenReturn(ChatbotAccessService.ClassroomAccess.STUDENT);
        when(activityRepository.findActivitiesByClassroomId(classroom)).thenReturn(List.of(
                activity(classroom, "Public exercise", ActivityStatus.PUBLISHED),
                activity(classroom, "SECRET exam answer key", ActivityStatus.DRAFT)));
        String context = service.buildClassroomContext(user, classroom, ChatbotAccessService.ClassroomAccess.STUDENT);
        assertTrue(context.contains("Public exercise"));
        assertFalse(context.contains("SECRET"));
        verify(activityRepository).findStudentActivities(classroom, user);
        verifyNoInteractions(studentActivityInfoRepository);
    }

    @Test
    void deniedOrRevokedAccessDoesNotQueryAnyPrivateData() {
        UUID user = UUID.randomUUID(), classroom = UUID.randomUUID();
        when(accessService.getAccess(user, classroom)).thenReturn(ChatbotAccessService.ClassroomAccess.NONE);
        assertTrue(service.buildClassroomContext(user, classroom, ChatbotAccessService.ClassroomAccess.STUDENT).isEmpty());
        verifyNoInteractions(activityRepository, studentActivityInfoRepository, announcementRepository);
    }

    @Test
    void submittedWorkWithMissingRepositoryIsNotPendingAndGradingDoesNotInventFailure() {
        UUID user = UUID.randomUUID(), classroom = UUID.randomUUID();
        Activity submitted = activity(classroom, "Already submitted", ActivityStatus.PUBLISHED);
        Activity graded = activity(classroom, "Forty points out of fifty", ActivityStatus.PUBLISHED);
        StudentActivityOverviewData submittedView = mock(StudentActivityOverviewData.class);
        when(submittedView.activityId()).thenReturn(submitted.getActivityId());
        when(submittedView.submissionStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        StudentActivityOverviewData gradedView = mock(StudentActivityOverviewData.class);
        when(gradedView.activityId()).thenReturn(graded.getActivityId());
        when(gradedView.submissionStatus()).thenReturn(SubmissionStatus.GRADED);
        when(gradedView.score()).thenReturn(40);
        when(accessService.getAccess(user, classroom)).thenReturn(ChatbotAccessService.ClassroomAccess.STUDENT);
        when(activityRepository.findActivitiesByClassroomId(classroom)).thenReturn(List.of(submitted, graded));
        when(activityRepository.findStudentActivities(classroom, user)).thenReturn(List.of(submittedView, gradedView));
        String context = service.buildClassroomContext(user, classroom, ChatbotAccessService.ClassroomAccess.STUDENT);
        assertTrue(context.contains("Activities Currently Requiring Student Action: 0"));
        assertTrue(context.contains("Own Submissions Awaiting Grading: 1"));
        assertTrue(context.contains("Own Graded Activities: 1"));
        assertFalse(context.contains("RESULT IS FAIL"));
        assertTrue(context.contains("Maximum Score: 50"));
    }

    @Test
    void dashboardIncludesAuthorizedClassroomActivitiesAndAnnouncementsWithoutPasscodes() {
        UUID user = UUID.randomUUID(), classroom = UUID.randomUUID(), owner = UUID.randomUUID();
        Instant now = Instant.now();
        ClassroomAggregate aggregate = new ClassroomAggregate(new Classroom(classroom, owner,
                "Programming", "Course", "CLASSCODE", ClassroomStatus.ACTIVE, now, now),
                new ClassroomSettings(classroom, false, "DO-NOT-SEND-PASSCODE", 30));
        when(classroomStudentRepository.findActiveEnrollmentsWithActiveClassroom(user))
                .thenReturn(List.of(ClassroomStudent.createActiveStudent(classroom, user)));
        when(classroomRepository.findAllById(List.of(classroom))).thenReturn(List.of(aggregate));
        when(classroomRepository.findByClassroomId(classroom)).thenReturn(Optional.of(aggregate));
        when(accessService.getAccess(user, classroom)).thenReturn(ChatbotAccessService.ClassroomAccess.STUDENT);
        when(activityRepository.findActivitiesByClassroomId(classroom))
                .thenReturn(List.of(activity(classroom, "Homework from dashboard", ActivityStatus.PUBLISHED)));
        when(announcementRepository.findAllByClassroomId(classroom))
                .thenReturn(List.of(Announcement.create(classroom, owner, "Bring your laptop", List.of(), now)));
        String context = service.buildDashboardContext(user, "Do I have homework?");
        assertTrue(context.contains("Homework from dashboard"));
        assertTrue(context.contains("Bring your laptop"));
        assertTrue(context.contains("Activities Currently Requiring Student Action: 1"));
        assertFalse(context.contains("DO-NOT-SEND-PASSCODE"));
        assertFalse(context.contains("CLASSCODE"));
        verifyNoInteractions(studentActivityInfoRepository);
    }

    @Test
    void checklistOrdersUnsubmittedWorkByDeadlineAndReportsMissingRepositories() {
        UUID user = UUID.randomUUID(), classroom = UUID.randomUUID();
        Instant now = Instant.now();
        Activity later = Activity.reconstitute(UUID.randomUUID(), classroom, UUID.randomUUID(),
                "Later work", "Instructions", now.plusSeconds(172800), ActivityStatus.PUBLISHED, 100, now, now);
        Activity earlier = Activity.reconstitute(UUID.randomUUID(), classroom, UUID.randomUUID(),
                "Earlier work", "Instructions", now.plusSeconds(86400), ActivityStatus.PUBLISHED, 100, now, now);
        when(accessService.getAccess(user, classroom)).thenReturn(ChatbotAccessService.ClassroomAccess.STUDENT);
        when(activityRepository.findActivitiesByClassroomId(classroom)).thenReturn(List.of(later, earlier));
        String context = service.buildClassroomContext(user, classroom, ChatbotAccessService.ClassroomAccess.STUDENT);
        assertTrue(context.contains("Published Pending Activities Missing Repository: 2"));
        assertTrue(context.indexOf("Checklist Activity: Earlier work") < context.indexOf("Checklist Activity: Later work"));
        assertTrue(context.contains("LINK_OR_CREATE_REPOSITORY, THEN COMMIT/PUSH AND SUBMIT"));
    }

    @Test
    void professorSummarySeparatesStudentsWithoutSubmissionsFromWorkAwaitingGrading() {
        UUID user = UUID.randomUUID(), classroom = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Activity task = activity(classroom, "Published work", ActivityStatus.PUBLISHED);
        when(accessService.getAccess(user, classroom)).thenReturn(ChatbotAccessService.ClassroomAccess.INSTRUCTOR);
        when(activityRepository.findActivitiesByClassroomId(classroom)).thenReturn(List.of(task));
        when(studentActivityInfoRepository.findClassroomStudents(classroom)).thenReturn(List.of(
                new StudentSummaryData(first, "Ana", "Student", null),
                new StudentSummaryData(second, "Ben", "Student", null)));
        StudentSubmissionDetailsData submission = mock(StudentSubmissionDetailsData.class);
        when(submission.userId()).thenReturn(first);
        when(submission.activityId()).thenReturn(task.getActivityId());
        when(submission.submissionStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(studentActivityInfoRepository.findStudentActivityInfos(classroom)).thenReturn(List.of(submission));
        String context = service.buildClassroomContext(user, classroom, ChatbotAccessService.ClassroomAccess.INSTRUCTOR);
        assertTrue(context.contains("Published Submissions Awaiting Grading: 1"));
        assertTrue(context.contains("Students With No Submitted Published Work: 1"));
        assertTrue(context.contains("Student With No Submitted Published Work: Ben Student"));
        assertFalse(context.contains("Student With No Submitted Published Work: Ana Student"));
    }

    private Activity activity(UUID classroom, String title, ActivityStatus status) {
        Instant now = Instant.now();
        return Activity.reconstitute(UUID.randomUUID(), classroom, UUID.randomUUID(), title,
                "Instructions", now.plusSeconds(86400), status, 50, now, now);
    }
}
