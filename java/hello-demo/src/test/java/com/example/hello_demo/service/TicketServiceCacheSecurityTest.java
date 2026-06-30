package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.dto.TicketAssigneeUpdateRequest;
import com.example.hello_demo.dto.TicketCategoryUpdateRequest;
import com.example.hello_demo.dto.TicketQueryRequest;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.enums.SlaStatus;
import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.dto.TicketCreateDTO;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.vo.TicketDetailVO;
import com.example.hello_demo.vo.UserInfoVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceCacheSecurityTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(Ticket.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, Ticket.class);
        }
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void cachedDetailStillChecksUserPermission() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(2L, "other-user", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketDetail(1L)
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void ownerCanReadCachedDetail() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(1L, "owner", "USER");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertSame(cached, result);
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void staffCanReadCachedDetailForAnyTicket() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(3L, "staff", "STAFF");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertSame(cached, result);
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void adminCanReadCachedDetailForAnyTicket() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(9L, "admin", "ADMIN");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertSame(cached, result);
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void normalUserCannotReadCachedAdminTicketDetail() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(2L, 9L),
                new UserInfoVO(9L, "admin", "Admin", null, "admin@example.com", "ADMIN"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(2L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketDetail(2L)
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void normalUserCannotUpdateTicketStatus() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(1L, TicketStatus.PROCESSING.name())
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void staffCanUpdateTicketStatus() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Ticket ticket = ticket(1L, 1L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(2L, "staff", "STAFF");

        Ticket result = ticketService.updateTicketStatus(1L, TicketStatus.PROCESSING.name());

        assertEquals(TicketStatus.PROCESSING.name(), result.getStatus());
        verify(ticketMapper).updateById(any(Ticket.class));
        verify(ticketCacheService).evictTicketRelated(1L);
    }

    @Test
    void createTicketUsesCurrentUserFromContext() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        AtomicReference<Ticket> inserted = new AtomicReference<>();
        when(userMapper.selectById(7L)).thenReturn(user(7L));
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(20L);
            inserted.set(ticket);
            return 1;
        }).when(ticketMapper).insert(any(Ticket.class));
        when(ticketMapper.selectById(20L)).thenAnswer(invocation -> inserted.get());
        TicketService ticketService = new TicketService(
                ticketMapper,
                userMapper,
                mock(TicketReplyMapper.class),
                operationLogService,
                ticketCacheService,
                new TicketStatusTransitionPolicy(),
                new SlaPolicy()
        );
        CurrentUserContext.set(7L, "tom", "USER");
        TicketCreateDTO dto = new TicketCreateDTO();
        dto.setTitle("Cannot login");
        dto.setContent("User cannot login with correct password");
        dto.setPriority("HIGH");

        Ticket created = ticketService.createTicket(dto);

        assertNotNull(created);
        assertEquals(7L, inserted.get().getUserId());
        assertEquals(TicketStatus.OPEN.name(), inserted.get().getStatus());
        verify(userMapper).selectById(7L);
        verify(operationLogService).record(
                eq("CREATE_TICKET"),
                eq("TICKET"),
                eq(20L),
                eq("用户创建了工单 #20")
        );
    }

    @Test
    void ticketDetailReturnsCategoryAndAssignedTo() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketReplyMapper ticketReplyMapper = mock(TicketReplyMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Ticket ticket = ticket(1L, 7L);
        ticket.setCategory("账号登录");
        ticket.setAssignedTo(2L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(userMapper.selectById(7L)).thenReturn(user(7L));
        TicketService ticketService = new TicketService(
                ticketMapper,
                userMapper,
                ticketReplyMapper,
                mock(OperationLogService.class),
                ticketCacheService,
                new TicketStatusTransitionPolicy(),
                new SlaPolicy()
        );
        CurrentUserContext.set(3L, "staff", "STAFF");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertEquals("账号登录", result.getTicket().getCategory());
        assertEquals(2L, result.getTicket().getAssignedTo());
    }

    @Test
    void ticketDetailReturnsReplyAuthorDisplayFields() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketReplyMapper ticketReplyMapper = mock(TicketReplyMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketReply reply = new TicketReply();
        reply.setId(10L);
        reply.setTicketId(1L);
        reply.setUserId(2L);
        reply.setContent("Please provide more detail");
        reply.setReplyType("STAFF");
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 7L));
        when(userMapper.selectById(7L)).thenReturn(user(7L));
        when(ticketReplyMapper.selectList(any())).thenReturn(List.of(reply));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(2L, "STAFF")));
        TicketService ticketService = new TicketService(
                ticketMapper,
                userMapper,
                ticketReplyMapper,
                mock(OperationLogService.class),
                ticketCacheService,
                new TicketStatusTransitionPolicy(),
                new SlaPolicy()
        );
        CurrentUserContext.set(3L, "staff", "STAFF");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertEquals("user-2", result.getReplies().get(0).authorName());
        assertEquals("STAFF", result.getReplies().get(0).authorRole());
    }

    @Test
    void ticketListReturnsCategoryAndAssignedTo() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Ticket ticket = ticket(1L, 7L);
        ticket.setCategory("账号登录");
        ticket.setAssignedTo(2L);
        Page<Ticket> page = new Page<>(1, 10);
        page.setRecords(List.of(ticket));
        page.setTotal(1);
        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(3L, "staff", "STAFF");

        List<Ticket> records = ticketService.getTickets(null).getRecords();

        assertEquals("账号登录", records.get(0).getCategory());
        assertEquals(2L, records.get(0).getAssignedTo());
    }

    @Test
    void createTicketGeneratesSlaDeadlinesFromPriority() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        AtomicReference<Ticket> inserted = new AtomicReference<>();
        when(userMapper.selectById(7L)).thenReturn(user(7L));
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(21L);
            inserted.set(ticket);
            return 1;
        }).when(ticketMapper).insert(any(Ticket.class));
        when(ticketMapper.selectById(21L)).thenAnswer(invocation -> inserted.get());
        TicketService ticketService = ticketService(ticketMapper, userMapper, operationLogService, ticketCacheService);
        CurrentUserContext.set(7L, "tom", "USER");

        Ticket created = ticketService.createTicket(createDto("URGENT"));

        assertNotNull(created.getResponseDueAt());
        assertNotNull(created.getResolveDueAt());
        assertEquals(inserted.get().getCreatedAt().plusMinutes(30), inserted.get().getResponseDueAt());
        assertEquals(inserted.get().getCreatedAt().plusHours(4), inserted.get().getResolveDueAt());
        assertEquals(SlaStatus.AT_RISK, created.getSlaStatus());
    }

    @Test
    void createTicketUsesMediumSlaWhenPriorityMissing() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        AtomicReference<Ticket> inserted = new AtomicReference<>();
        when(userMapper.selectById(7L)).thenReturn(user(7L));
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(22L);
            inserted.set(ticket);
            return 1;
        }).when(ticketMapper).insert(any(Ticket.class));
        when(ticketMapper.selectById(22L)).thenAnswer(invocation -> inserted.get());
        TicketService ticketService = ticketService(ticketMapper, userMapper, mock(OperationLogService.class), ticketCacheService);
        CurrentUserContext.set(7L, "tom", "USER");

        Ticket created = ticketService.createTicket(createDto(null));

        assertEquals("MEDIUM", inserted.get().getPriority());
        assertEquals(inserted.get().getCreatedAt().plusHours(8), created.getResponseDueAt());
        assertEquals(inserted.get().getCreatedAt().plusHours(72), created.getResolveDueAt());
    }

    @Test
    void closedStatusWritesClosedAt() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Ticket ticket = ticket(1L, 1L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(2L, "staff", "STAFF");

        Ticket result = ticketService.updateTicketStatus(1L, TicketStatus.CLOSED.name());

        assertEquals(TicketStatus.CLOSED.name(), result.getStatus());
        assertNotNull(result.getClosedAt());
        assertEquals(SlaStatus.COMPLETED, result.getSlaStatus());
    }

    @Test
    void ticketListDecoratesOverdueSlaStatus() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        Ticket overdue = ticket(1L, 7L);
        overdue.setResolveDueAt(java.time.LocalDateTime.now().minusMinutes(5));
        Page<Ticket> page = new Page<>(1, 10);
        page.setRecords(List.of(overdue));
        page.setTotal(1);
        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");

        Ticket result = ticketService.getTickets(null).getRecords().get(0);

        assertEquals(SlaStatus.OVERDUE, result.getSlaStatus());
        assertEquals(true, result.getSlaOverdue());
    }

    @Test
    void ticketListDecoratesAtRiskSlaStatus() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        Ticket atRisk = ticket(1L, 7L);
        atRisk.setResolveDueAt(java.time.LocalDateTime.now().plusHours(2));
        Page<Ticket> page = new Page<>(1, 10);
        page.setRecords(List.of(atRisk));
        page.setTotal(1);
        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");

        Ticket result = ticketService.getTickets(null).getRecords().get(0);

        assertEquals(SlaStatus.AT_RISK, result.getSlaStatus());
        assertEquals(false, result.getSlaOverdue());
    }

    @Test
    void ticketListSupportsUnassignedAssigneeFilterInSqlWrapper() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Page<Ticket> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketQueryRequest request = new TicketQueryRequest();
        request.setAssignedTo("unassigned");

        ticketService.getTickets(request);

        var wrapperCaptor = forClass(LambdaQueryWrapper.class);
        verify(ticketMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = ((LambdaQueryWrapper<?>) wrapperCaptor.getValue()).getSqlSegment().toLowerCase();
        assertEquals(true, sqlSegment.contains("assigned_to is null"));
    }

    @Test
    void ticketListSupportsMeAssigneeFilterInSqlWrapper() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Page<Ticket> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketQueryRequest request = new TicketQueryRequest();
        request.setAssignedTo("me");

        ticketService.getTickets(request);

        var wrapperCaptor = forClass(LambdaQueryWrapper.class);
        verify(ticketMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = ((LambdaQueryWrapper<?>) wrapperCaptor.getValue()).getSqlSegment().toLowerCase();
        assertEquals(true, sqlSegment.contains("assigned_to ="));
    }

    @Test
    void staffCannotListTicketsAssignedToOtherUsers() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketQueryRequest request = new TicketQueryRequest();
        request.setAssignedTo("4");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTickets(request)
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void staffCanUpdateCategoryAndEvictCacheAndWriteLog() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        Ticket existing = ticket(1L, 7L);
        existing.setCategory("旧分类");
        Ticket updated = ticket(1L, 7L);
        updated.setCategory("账号登录");
        when(ticketMapper.selectById(1L)).thenReturn(existing, updated);
        when(ticketMapper.update(isNull(), any())).thenReturn(1);
        TicketService ticketService = ticketService(ticketMapper, mock(UserMapper.class), operationLogService, ticketCacheService);
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketCategoryUpdateRequest request = new TicketCategoryUpdateRequest();
        request.setCategory("账号登录");

        Ticket result = ticketService.updateTicketCategory(1L, request);

        assertEquals("账号登录", result.getCategory());
        verify(ticketMapper).update(isNull(), any());
        verify(ticketCacheService).evictTicketRelated(1L);
        verify(operationLogService).record(
                eq(OperationType.TICKET_CATEGORY_UPDATED.name()),
                eq("TICKET"),
                eq(1L),
                eq("工单分类从 [旧分类] 修改为 [账号登录]")
        );
    }

    @Test
    void adminCanClearCategory() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        Ticket existing = ticket(1L, 7L);
        existing.setCategory("旧分类");
        Ticket updated = ticket(1L, 7L);
        updated.setCategory(null);
        when(ticketMapper.selectById(1L)).thenReturn(existing, updated);
        when(ticketMapper.update(isNull(), any())).thenReturn(1);
        TicketService ticketService = ticketService(ticketMapper, mock(UserMapper.class), mock(OperationLogService.class), mock(TicketCacheService.class));
        CurrentUserContext.set(9L, "admin", "ADMIN");

        Ticket result = ticketService.updateTicketCategory(1L, new TicketCategoryUpdateRequest());

        assertEquals(null, result.getCategory());
    }

    @Test
    void normalUserCannotUpdateCategory() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketCategory(1L, new TicketCategoryUpdateRequest())
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void updateCategoryForMissingTicketReturns404() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        when(ticketMapper.selectById(99L)).thenReturn(null);
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketCategory(99L, new TicketCategoryUpdateRequest())
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void updateCategoryRejectsTooLongValue() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 7L));
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketCategoryUpdateRequest request = new TicketCategoryUpdateRequest();
        request.setCategory("x".repeat(65));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketCategory(1L, request)
        );

        assertEquals(400, exception.getCode());
    }

    @Test
    void adminCanAssignTicketToStaffAndWriteLog() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        Ticket existing = ticket(1L, 7L);
        Ticket updated = ticket(1L, 7L);
        updated.setAssignedTo(3L);
        when(ticketMapper.selectById(1L)).thenReturn(existing, updated);
        when(ticketMapper.update(isNull(), any())).thenReturn(1);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "STAFF"));
        TicketService ticketService = ticketService(ticketMapper, userMapper, operationLogService, ticketCacheService);
        CurrentUserContext.set(9L, "admin", "ADMIN");
        TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
        request.setAssignedTo(3L);

        Ticket result = ticketService.updateTicketAssignee(1L, request);

        assertEquals(3L, result.getAssignedTo());
        verify(ticketCacheService).evictTicketRelated(1L);
        verify(operationLogService).record(
                eq(OperationType.TICKET_ASSIGNEE_UPDATED.name()),
                eq("TICKET"),
                eq(1L),
                eq("工单处理人从 [未分配] 修改为 [user-3]")
        );
    }

    @Test
    void adminCanClearAssignee() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        Ticket existing = ticket(1L, 7L);
        existing.setAssignedTo(3L);
        Ticket updated = ticket(1L, 7L);
        updated.setAssignedTo(null);
        when(ticketMapper.selectById(1L)).thenReturn(existing, updated);
        when(ticketMapper.update(isNull(), any())).thenReturn(1);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "STAFF"));
        TicketService ticketService = ticketService(ticketMapper, userMapper, mock(OperationLogService.class), mock(TicketCacheService.class));
        CurrentUserContext.set(9L, "admin", "ADMIN");

        Ticket result = ticketService.updateTicketAssignee(1L, new TicketAssigneeUpdateRequest());

        assertEquals(null, result.getAssignedTo());
    }

    @Test
    void staffCanAssignTicketToSelf() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        Ticket updated = ticket(1L, 7L);
        updated.setAssignedTo(3L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 7L), updated);
        when(ticketMapper.update(isNull(), any())).thenReturn(1);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "STAFF"));
        TicketService ticketService = ticketService(ticketMapper, userMapper, mock(OperationLogService.class), mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
        request.setAssignedTo(3L);

        Ticket result = ticketService.updateTicketAssignee(1L, request);

        assertEquals(3L, result.getAssignedTo());
    }

    @Test
    void staffCannotAssignTicketToOtherUser() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 7L));
        TicketService ticketService = ticketService(ticketMapper, mock(UserMapper.class), mock(OperationLogService.class), mock(TicketCacheService.class));
        CurrentUserContext.set(3L, "staff", "STAFF");
        TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
        request.setAssignedTo(4L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketAssignee(1L, request)
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).update(isNull(), any());
    }

    @Test
    void normalUserCannotAssignTicket() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketService ticketService = ticketService(ticketMapper, mock(TicketCacheService.class));
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketAssignee(1L, new TicketAssigneeUpdateRequest())
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void assigningMissingUserReturns400() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 7L));
        when(userMapper.selectById(99L)).thenReturn(null);
        TicketService ticketService = ticketService(ticketMapper, userMapper, mock(OperationLogService.class), mock(TicketCacheService.class));
        CurrentUserContext.set(9L, "admin", "ADMIN");
        TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
        request.setAssignedTo(99L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketAssignee(1L, request)
        );

        assertEquals(400, exception.getCode());
    }

    @Test
    void assigningNormalUserReturns400() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 7L));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "USER"));
        TicketService ticketService = ticketService(ticketMapper, userMapper, mock(OperationLogService.class), mock(TicketCacheService.class));
        CurrentUserContext.set(9L, "admin", "ADMIN");
        TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
        request.setAssignedTo(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketAssignee(1L, request)
        );

        assertEquals(400, exception.getCode());
    }

    private TicketService ticketService(TicketMapper ticketMapper, TicketCacheService ticketCacheService) {
        return ticketService(
                ticketMapper,
                mock(UserMapper.class),
                mock(OperationLogService.class),
                ticketCacheService
        );
    }

    private TicketService ticketService(TicketMapper ticketMapper, UserMapper userMapper, OperationLogService operationLogService, TicketCacheService ticketCacheService) {
        return new TicketService(
                ticketMapper,
                userMapper,
                mock(TicketReplyMapper.class),
                operationLogService,
                ticketCacheService,
                new TicketStatusTransitionPolicy(),
                new SlaPolicy()
        );
    }

    private TicketCreateDTO createDto(String priority) {
        TicketCreateDTO dto = new TicketCreateDTO();
        dto.setTitle("Cannot login");
        dto.setContent("User cannot login with correct password");
        dto.setPriority(priority);
        return dto;
    }

    private Ticket ticket(Long id, Long userId) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setUserId(userId);
        ticket.setTitle("title");
        ticket.setContent("content");
        ticket.setStatus(TicketStatus.OPEN.name());
        ticket.setPriority("MEDIUM");
        ticket.setCategory("OTHER");
        return ticket;
    }

    private User user(Long id) {
        return user(id, "USER");
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setName("user-" + id);
        user.setRole(role);
        return user;
    }
}
