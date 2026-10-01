package com.appmgr.api.controller;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.project.ProjectDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.project.CreateProjectForm;
import com.appmgr.api.form.project.UpdateProjectForm;
import com.appmgr.api.mapper.ProjectMapper;
import com.appmgr.api.model.Project;
import com.appmgr.api.model.criteria.ProjectCriteria;
import com.appmgr.api.repository.ApplicationRepository;
import com.appmgr.api.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ProjectController}.
 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Spy
    private ProjectMapper projectMapper = Mappers.getMapper(ProjectMapper.class);

    @InjectMocks
    private ProjectController controller;

    // ------------------------------------------------------------------ create

    @Test
    void shouldCreateProjectSuccessfully() {
        CreateProjectForm form = new CreateProjectForm();
        form.setName("New Project");
        form.setDescription("New Description");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");

        when(projectRepository.existsByName("New Project")).thenReturn(false);

        ApiMessageDto<ProjectDto> result = controller.create(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Create project success");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void shouldThrowBadRequestWhenCreateNameAlreadyExists() {
        CreateProjectForm form = new CreateProjectForm();
        form.setName("Existing Project");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");

        when(projectRepository.existsByName("Existing Project")).thenReturn(true);

        assertThatThrownBy(() -> controller.create(form, bindingResult))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PROJECT_ERROR_NAME_EXIST);
        verify(projectRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ get

    @Test
    void shouldReturnProjectWhenIdExists() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");
        project.setDescription("Test Description");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        ApiMessageDto<ProjectDto> result = controller.get(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(1L);
        assertThat(result.getData().getName()).isEqualTo("Test Project");
        assertThat(result.getMessage()).isEqualTo("Get project success");
    }

    @Test
    void shouldThrowNotFoundWhenGetIdDoesNotExist() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PROJECT_ERROR_NOT_FOUND);
    }

    // ------------------------------------------------------------------ update

    @Test
    void shouldUpdateProjectSuccessfully() {
        UpdateProjectForm form = new UpdateProjectForm();
        form.setId(1L);
        form.setName("Updated Project");
        form.setDescription("Updated Description");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");

        Project project = new Project();
        project.setId(1L);
        project.setName("Old Project");
        project.setDescription("Old Description");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByNameAndIdNot("Updated Project", 1L)).thenReturn(false);

        ApiMessageDto<Void> result = controller.update(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update project success");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void shouldThrowBadRequestWhenUpdateNameAlreadyExists() {
        UpdateProjectForm form = new UpdateProjectForm();
        form.setId(1L);
        form.setName("Another Project");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");

        Project project = new Project();
        project.setId(1L);
        project.setName("Old Project");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByNameAndIdNot("Another Project", 1L)).thenReturn(true);

        assertThatThrownBy(() -> controller.update(form, bindingResult))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PROJECT_ERROR_NAME_EXIST);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldNotCheckNameExistsWhenNameUnchangedOnUpdate() {
        UpdateProjectForm form = new UpdateProjectForm();
        form.setId(1L);
        form.setName("Test Project");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");

        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        ApiMessageDto<Void> result = controller.update(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        verify(projectRepository, never()).existsByNameAndIdNot(anyString(), any());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void shouldThrowNotFoundWhenUpdateIdDoesNotExist() {
        UpdateProjectForm form = new UpdateProjectForm();
        form.setId(1L);
        form.setName("Test Project");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");

        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(form, bindingResult))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PROJECT_ERROR_NOT_FOUND);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void shouldThrowNotFoundWhenDeleteIdDoesNotExist() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.delete(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PROJECT_ERROR_NOT_FOUND);
    }

    @Test
    void shouldCascadeDeleteApplicationsBeforeDeletingProject() {
        Project project = new Project();
        project.setId(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        ApiMessageDto<Void> result = controller.delete(1L);

        assertThat(result.getResult()).isTrue();
        InOrder order = inOrder(applicationRepository, projectRepository);
        order.verify(applicationRepository).deleteAllByProjectId(1L);
        order.verify(projectRepository).delete(project);
    }

    // ------------------------------------------------------------------ list

    @Test
    @SuppressWarnings("unchecked")
    void shouldListProjectsSuccessfully() {
        ProjectCriteria criteria = new ProjectCriteria();
        Pageable pageable = PageRequest.of(0, 10);

        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");
        project.setDescription("Test Description");
        Page<Project> page = new PageImpl<>(Collections.singletonList(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ApiMessageDto<ResponseListDto<List<ProjectDto>>> result = controller.list(criteria, pageable);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).hasSize(1);
        assertThat(result.getData().getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getData().getContent().get(0).getName()).isEqualTo("Test Project");
        assertThat(result.getData().getTotalPages()).isEqualTo(1);
        assertThat(result.getData().getTotalElements()).isEqualTo(1L);
        assertThat(result.getMessage()).isEqualTo("Get list success");
    }
}
