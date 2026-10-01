package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.category.CategoryDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.category.CreateCategoryForm;
import com.appmgr.api.form.category.UpdateCategoryForm;
import com.appmgr.api.mapper.CategoryMapper;
import com.appmgr.api.model.Category;
import com.appmgr.api.model.criteria.CategoryCriteria;
import com.appmgr.api.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BindingResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link CategoryController}.
 */
@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryController categoryController;

    // ------------------------------------------------------------------ create

    @Test
    void shouldThrowBadRequestWhenCreateNameAlreadyExists() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        CreateCategoryForm form = new CreateCategoryForm();
        form.setName("Category One");

        when(categoryRepository.existsByName("Category One")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> categoryController.create(form, bindingResult))
                .isInstanceOf(BadRequestException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldCreateCategorySuccessfully() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        CreateCategoryForm form = new CreateCategoryForm();
        form.setName("Category One");

        Category category = new Category();
        category.setName("Category One");

        when(categoryRepository.existsByName("Category One")).thenReturn(false);
        when(categoryMapper.fromCreateCategoryFormToEntity(form)).thenReturn(category);

        // Act
        ApiMessageDto<Void> result = categoryController.create(form, bindingResult);

        // Assert
        assertThat(result.getResult()).isTrue();
        verify(categoryRepository).save(category);
    }

    // ------------------------------------------------------------------ update

    @Test
    void shouldThrowNotFoundWhenUpdateIdMissing() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        UpdateCategoryForm form = new UpdateCategoryForm();
        form.setId(1L);
        form.setName("Category One");

        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> categoryController.update(form, bindingResult))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowBadRequestWhenUpdateNameExistsExcludingSelf() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        UpdateCategoryForm form = new UpdateCategoryForm();
        form.setId(1L);
        form.setName("Category Two");

        Category category = new Category();
        category.setId(1L);
        category.setName("Category One");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Category Two", 1L)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> categoryController.update(form, bindingResult))
                .isInstanceOf(BadRequestException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldNotCheckNameExistsWhenNameUnchangedOnUpdate() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        UpdateCategoryForm form = new UpdateCategoryForm();
        form.setId(1L);
        form.setName("Category One");

        Category category = new Category();
        category.setId(1L);
        category.setName("Category One");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        // Act
        ApiMessageDto<Void> result = categoryController.update(form, bindingResult);

        // Assert
        assertThat(result.getResult()).isTrue();
        verify(categoryRepository, never()).existsByNameAndIdNot(anyString(), any());
        verify(categoryRepository).save(category);
    }

    // -------------------------------------------------------------------- list

    @Test
    @SuppressWarnings("unchecked")
    void shouldListCategoriesWithNameFilter() {
        // Arrange
        CategoryCriteria criteria = new CategoryCriteria();
        criteria.setName("Book");
        Pageable pageable = PageRequest.of(0, 10);

        Category category = new Category();
        category.setId(1L);
        category.setName("Books");
        Page<Category> page = new PageImpl<>(Collections.singletonList(category), pageable, 1);

        CategoryDto dto = new CategoryDto();
        dto.setId(1L);
        dto.setName("Books");

        when(categoryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(categoryMapper.fromEntityToCategoryDtoList(anyList())).thenReturn(Collections.singletonList(dto));

        // Act
        ApiMessageDto<ResponseListDto<List<CategoryDto>>> result = categoryController.list(criteria, pageable);

        // Assert
        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).hasSize(1);
        assertThat(result.getData().getContent().get(0).getName()).isEqualTo("Books");
        verify(categoryRepository).findAll(any(Specification.class), eq(pageable));
    }

    // ------------------------------------------------------------ auto-complete

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnTrimmedIdAndNameOnlyForAutoComplete() {
        // Arrange (auto-complete trims CategoryDto down to id + name only, page fixed to (0, 10))
        CategoryCriteria criteria = new CategoryCriteria();
        criteria.setName("Book");
        Pageable pageable = PageRequest.of(0, 10);

        Category category = new Category();
        category.setId(1L);
        category.setName("Books");
        category.setDescription("Book category");
        Page<Category> page = new PageImpl<>(Collections.singletonList(category), pageable, 1);

        CategoryDto dto = new CategoryDto();
        dto.setId(1L);
        dto.setName("Books");

        when(categoryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(categoryMapper.fromEntityToCategoryAutoCompleteDtoList(anyList())).thenReturn(Collections.singletonList(dto));

        // Act
        ApiMessageDto<ResponseListDto<List<CategoryDto>>> result = categoryController.autoComplete(criteria);

        // Assert
        assertThat(criteria.getStatus()).isEqualTo(BaseConstant.STATUS_ACTIVE);
        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).hasSize(1);
        CategoryDto returned = result.getData().getContent().get(0);
        assertThat(returned.getId()).isEqualTo(1L);
        assertThat(returned.getName()).isEqualTo("Books");
        assertThat(returned.getDescription()).isNull();
        verify(categoryRepository).findAll(any(Specification.class), eq(pageable));
        verify(categoryMapper, never()).fromEntityToCategoryDtoList(anyList());
    }

    // --------------------------------------------------------------------- get

    @Test
    void shouldThrowNotFoundWhenGetIdMissing() {
        // Arrange
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> categoryController.get(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldGetCategorySuccessfully() {
        // Arrange
        Category category = new Category();
        category.setId(1L);
        category.setName("Category One");

        CategoryDto dto = new CategoryDto();
        dto.setId(1L);
        dto.setName("Category One");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryMapper.fromEntityToCategoryDto(category)).thenReturn(dto);

        // Act
        ApiMessageDto<CategoryDto> result = categoryController.get(1L);

        // Assert
        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isEqualTo(dto);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void shouldThrowNotFoundWhenDeleteIdMissing() {
        // Arrange
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> categoryController.delete(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldDeleteCategoryWhenIdExists() {
        // Arrange
        Category category = new Category();
        category.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        // Act
        ApiMessageDto<Void> result = categoryController.delete(1L);

        // Assert
        assertThat(result.getResult()).isTrue();
        verify(categoryRepository).deleteById(1L);
    }
}
