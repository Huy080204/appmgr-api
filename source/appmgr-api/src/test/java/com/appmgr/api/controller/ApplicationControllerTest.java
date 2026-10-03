package com.appmgr.api.controller;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.application.ApplicationDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.application.CreateApplicationForm;
import com.appmgr.api.form.application.UpdateApplicationForm;
import com.appmgr.api.mapper.ApplicationMapper;
import com.appmgr.api.mapper.ProjectMapper;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Project;
import com.appmgr.api.repository.ApplicationRepository;
import com.appmgr.api.repository.ProjectRepository;
import com.appmgr.api.repository.VersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ApplicationController}.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private VersionRepository versionRepository;

    @Spy
    private ApplicationMapper applicationMapper = Mappers.getMapper(ApplicationMapper.class);

    @InjectMocks
    private ApplicationController controller;

    private Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    private Application application(Long id, String name, Project project) {
        Application application = new Application();
        application.setId(id);
        application.setName(name);
        application.setProject(project);
        return application;
    }

    private UpdateApplicationForm updateForm(Long id, String name) {
        UpdateApplicationForm form = new UpdateApplicationForm();
        form.setId(id);
        form.setName(name);
        return form;
    }

    // ------------------------------------------------------------------ create

    @Test
    void shouldCreateApplicationSuccessfully() {
        CreateApplicationForm form = new CreateApplicationForm();
        form.setName("App A");
        form.setProjectId(1L);
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        when(applicationRepository.existsByNameAndProjectId("App A", 1L)).thenReturn(false);

        ApiMessageDto<ApplicationDto> result = controller.create(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Create application success");
        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void shouldThrowBadRequestWhenCreateNameExistsInSameProject() {
        CreateApplicationForm form = new CreateApplicationForm();
        form.setName("App A");
        form.setProjectId(1L);
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        when(applicationRepository.existsByNameAndProjectId("App A", 1L)).thenReturn(true);

        assertThatThrownBy(() -> controller.create(form, bindingResult))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NAME_EXIST);
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void shouldThrowNotFoundWhenCreateProjectDoesNotExist() {
        CreateApplicationForm form = new CreateApplicationForm();
        form.setName("App A");
        form.setProjectId(9L);
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(projectRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(form, bindingResult))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PROJECT_ERROR_NOT_FOUND);
        verify(applicationRepository, never()).save(any(Application.class));
    }

    // ------------------------------------------------------------------ get

    @Test
    void shouldReturnApplicationWithProjectWhenGetIdExists() {
        Project project = project(1L);
        project.setName("Project A");
        ReflectionTestUtils.setField(applicationMapper, "projectMapper", Mappers.getMapper(ProjectMapper.class));
        Application existing = application(5L, "App A", project);
        existing.setDescription("Desc A");
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));

        ApiMessageDto<ApplicationDto> result = controller.get(5L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Get application success");
        assertThat(result.getData().getId()).isEqualTo(5L);
        assertThat(result.getData().getName()).isEqualTo("App A");
        assertThat(result.getData().getDescription()).isEqualTo("Desc A");
        assertThat(result.getData().getProject().getId()).isEqualTo(1L);
        assertThat(result.getData().getProject().getName()).isEqualTo("Project A");
    }

    @Test
    void shouldThrowNotFoundWhenGetIdDoesNotExist() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NOT_FOUND);
    }

    // ------------------------------------------------------------------ update

    @Test
    void shouldUpdateApplicationSuccessfully() {
        Application existing = application(5L, "Old", project(1L));
        UpdateApplicationForm form = updateForm(5L, "New");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(applicationRepository.existsByNameAndProjectIdAndIdNot("New", 1L, 5L)).thenReturn(false);

        ApiMessageDto<Void> result = controller.update(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update application success");
        assertThat(existing.getName()).isEqualTo("New");
        verify(applicationRepository).save(existing);
    }

    @Test
    void shouldKeepProjectUnchangedWhenUpdate() {
        Project originalProject = project(1L);
        Application existing = application(5L, "Old", originalProject);
        UpdateApplicationForm form = updateForm(5L, "New");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(applicationRepository.existsByNameAndProjectIdAndIdNot("New", 1L, 5L)).thenReturn(false);

        controller.update(form, bindingResult);

        assertThat(existing.getProject()).isSameAs(originalProject);
        verify(projectRepository, never()).findById(anyLong());
    }

    @Test
    void shouldThrowBadRequestWhenUpdateNameExistsInExistingProject() {
        Application existing = application(5L, "Old", project(1L));
        UpdateApplicationForm form = updateForm(5L, "Taken");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(applicationRepository.existsByNameAndProjectIdAndIdNot("Taken", 1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> controller.update(form, bindingResult))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NAME_EXIST);
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void shouldNotCheckNameExistsWhenNameUnchangedOnUpdate() {
        Application existing = application(5L, "Same", project(1L));
        UpdateApplicationForm form = updateForm(5L, "Same");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));

        ApiMessageDto<Void> result = controller.update(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        verify(applicationRepository, never())
                .existsByNameAndProjectIdAndIdNot(anyString(), anyLong(), anyLong());
        verify(applicationRepository).save(existing);
    }

    @Test
    void shouldThrowNotFoundWhenUpdateApplicationDoesNotExist() {
        UpdateApplicationForm form = updateForm(5L, "New");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(applicationRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(form, bindingResult))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NOT_FOUND);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void shouldDeleteVersionsBeforeDeletingApplication() {
        Application existing = application(5L, "App", project(1L));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));

        ApiMessageDto<Void> result = controller.delete(5L);

        assertThat(result.getResult()).isTrue();
        InOrder order = inOrder(versionRepository, applicationRepository);
        order.verify(versionRepository).deleteAllByApplicationId(5L);
        order.verify(applicationRepository).delete(existing);
    }

    @Test
    void shouldDeleteApplicationSuccessfully() {
        Application existing = application(5L, "App", project(1L));
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(existing));

        ApiMessageDto<Void> result = controller.delete(5L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Delete application success");
        verify(applicationRepository).delete(existing);
    }

    @Test
    void shouldThrowNotFoundWhenDeleteIdDoesNotExist() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.delete(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NOT_FOUND);
    }
}
