package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/v1/category")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class CategoryController extends ABasicController {
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryMapper categoryMapper;

    @Transactional
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CAT_C')")
    public ApiMessageDto<Void> create(@Valid @RequestBody CreateCategoryForm createCategoryForm, BindingResult bindingResult) {
        if (categoryRepository.existsByName(createCategoryForm.getName())) {
            throw new BadRequestException("Category name already exist", ErrorCode.CATEGORY_ERROR_NAME_EXIST);
        }
        Category category = categoryMapper.fromCreateCategoryFormToEntity(createCategoryForm);
        categoryRepository.save(category);
        return makeSuccessResponse("Create category success");
    }

    @Transactional
    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CAT_U')")
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateCategoryForm updateCategoryForm, BindingResult bindingResult) {
        Category category = categoryRepository.findById(updateCategoryForm.getId())
                .orElseThrow(() -> new NotFoundException("Category not found", ErrorCode.CATEGORY_ERROR_NOT_FOUND));
        if (!Objects.equals(updateCategoryForm.getName(), category.getName())
                && categoryRepository.existsByNameAndIdNot(updateCategoryForm.getName(), category.getId())) {
            throw new BadRequestException("Category name already exist", ErrorCode.CATEGORY_ERROR_NAME_EXIST);
        }
        categoryMapper.updateEntityFromForm(updateCategoryForm, category);
        categoryRepository.save(category);
        return makeSuccessResponse("Update category success");
    }

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CAT_V')")
    public ApiMessageDto<CategoryDto> get(@PathVariable("id") Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found", ErrorCode.CATEGORY_ERROR_NOT_FOUND));
        return makeSuccessResponse(categoryMapper.fromEntityToCategoryDto(category), "Get category success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CAT_L')")
    public ApiMessageDto<ResponseListDto<List<CategoryDto>>> list(CategoryCriteria criteria, Pageable pageable) {
        Page<Category> categories = categoryRepository.findAll(criteria.getCriteria(), pageable);
        return makeSuccessResponse(makeResponseListDto(categories, categoryMapper::fromEntityToCategoryDtoList), "List category success");
    }

    @Transactional
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CAT_D')")
    public ApiMessageDto<Void> delete(@PathVariable("id") Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found", ErrorCode.CATEGORY_ERROR_NOT_FOUND));
        categoryRepository.deleteById(id);
        return makeSuccessResponse("Delete category success");
    }

    @GetMapping(value = "/auto-complete", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CAT_L')")
    public ApiMessageDto<ResponseListDto<List<CategoryDto>>> autoComplete(CategoryCriteria criteria) {
        criteria.setStatus(BaseConstant.STATUS_ACTIVE);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Category> categories = categoryRepository.findAll(criteria.getCriteria(), pageable);
        return makeSuccessResponse(makeResponseListDto(categories, categoryMapper::fromEntityToCategoryAutoCompleteDtoList), "Get auto complete categories success");
    }
}
