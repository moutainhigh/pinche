/*
 * Copyright 2005-2017 shopxx.net. All rights reserved.
 * Support: http://www.shopxx.net
 * License: http://www.shopxx.net/license
 */
package net.shopxx.controller.business;

import net.shopxx.FileType;
import net.shopxx.Pageable;
import net.shopxx.Results;
import net.shopxx.entity.*;
import net.shopxx.exception.UnauthorizedException;
import net.shopxx.security.CurrentStore;
import net.shopxx.service.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;

/**
 * Controller - 商品
 *
 * @author SHOP++ Team
 * @version 5.0
 */
@Controller("businessProductController")
@RequestMapping("/business/product")
public class ProductController extends BaseController {

	@Inject
	private ProductService productService;
	@Inject
	private SkuService skuService;
	@Inject
	private StoreService storeService;
	@Inject
	private ProductCategoryService productCategoryService;
	@Inject
	private StoreProductCategoryService storeProductCategoryService;
	@Inject
	private BrandService brandService;
	@Inject
	private PromotionService promotionService;
	@Inject
	private ProductTagService productTagService;
	@Inject
	private StoreProductTagService storeProductTagService;
	@Inject
	private ProductImageService productImageService;
	@Inject
	private ParameterValueService parameterValueService;
	@Inject
	private SpecificationItemService specificationItemService;
	@Inject
	private AttributeService attributeService;
	@Inject
	private SpecificationService specificationService;
	@Inject
	private FileService fileService;
	@Autowired
	private MemberRankService memberRankService;

	/**
	 * 添加属性
	 */
	@ModelAttribute
	public void populateModel(Long productId, Long productCategoryId, @CurrentStore Store currentStore, ModelMap model) {
		Product product = productService.find(productId);
		if (product != null && !currentStore.equals(product.getStore())) {
			throw new UnauthorizedException();
		}
		ProductCategory productCategory = productCategoryService.find(productCategoryId);
		if (productCategory != null && !storeService.productCategoryExists(currentStore, productCategory)) {
			throw new UnauthorizedException();
		}

		model.addAttribute("product", product);
		model.addAttribute("productCategory", productCategory);
	}

	/**
	 * 检查编号是否存在
	 */
	@GetMapping("/check_sn")
	public @ResponseBody boolean checkSn(String sn) {
		return StringUtils.isNotEmpty(sn) && !productService.snExists(sn);
	}

	/**
	 * 上传商品图片
	 */
	@PostMapping("/upload_product_image")
	public ResponseEntity<?> uploadProductImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return Results.UNPROCESSABLE_ENTITY;
		}
		if (!fileService.isValid(FileType.image, file)) {
			return Results.unprocessableEntity("business.upload.invalid");
		}
		ProductImage productImage = productImageService.generate(file);
		if (productImage == null) {
			return Results.unprocessableEntity("business.upload.error");
		}

		return ResponseEntity.ok(productImage);
	}

	/**
	 * 删除商品图片
	 */
	@PostMapping("/delete_product_image")
	public ResponseEntity<?> deleteProductImage() {
		return Results.OK;
	}

	/**
	 * 获取参数
	 */
	@GetMapping("/parameters")
	public @ResponseBody List<Map<String, Object>> parameters(@ModelAttribute(binding = false) ProductCategory productCategory) {
		List<Map<String, Object>> data = new ArrayList<>();
		if (productCategory == null || CollectionUtils.isEmpty(productCategory.getParameters())) {
			return data;
		}
		for (Parameter parameter : productCategory.getParameters()) {
			Map<String, Object> item = new HashMap<>();
			item.put("group", parameter.getGroup());
			item.put("names", parameter.getNames());
			data.add(item);
		}
		return data;
	}

	/**
	 * 获取属性
	 */
	@GetMapping("/attributes")
	public @ResponseBody List<Map<String, Object>> attributes(@ModelAttribute(binding = false) ProductCategory productCategory) {
		List<Map<String, Object>> data = new ArrayList<>();
		if (productCategory == null || CollectionUtils.isEmpty(productCategory.getAttributes())) {
			return data;
		}
		for (Attribute attribute : productCategory.getAttributes()) {
			Map<String, Object> item = new HashMap<>();
			item.put("id", attribute.getId());
			item.put("name", attribute.getName());
			item.put("options", attribute.getOptions());
			data.add(item);
		}
		return data;
	}

	/**
	 * 获取规格
	 */
	@GetMapping("/specifications")
	public @ResponseBody List<Map<String, Object>> specifications(@ModelAttribute(binding = false) ProductCategory productCategory) {
		List<Map<String, Object>> data = new ArrayList<>();
		if (productCategory == null || CollectionUtils.isEmpty(productCategory.getSpecifications())) {
			return data;
		}
		for (Specification specification : productCategory.getSpecifications()) {
			Map<String, Object> item = new HashMap<>();
			item.put("name", specification.getName());
			item.put("options", specification.getOptions());
			data.add(item);
		}
		return data;
	}

	/**
	 * 添加
	 */
	@GetMapping("/add")
	public String add(@CurrentStore Store currentStore, ModelMap model, RedirectAttributes redirectAttributes) {
		Long productCount = productService.count(null, currentStore, null, null, null, null, null, null);
		if (currentStore.getStoreRank() != null && currentStore.getStoreRank().getQuantity() != null && productCount >= currentStore.getStoreRank().getQuantity()) {
			addFlashMessage(redirectAttributes, "business.product.addCountNotAllowed", currentStore.getStoreRank().getQuantity());
			return "redirect:list";
		}
		Business business = currentStore.getBusiness();
		boolean gradeGiftBag = business.getGradeGiftBag();
		model.addAttribute("gradeGiftBag",gradeGiftBag);
		model.addAttribute("types", Product.Type.values());
		model.addAttribute("productCategoryTree", productCategoryService.findTree());
		model.addAttribute("allowedProductCategories", productCategoryService.findList(currentStore, null, null, null));
		model.addAttribute("allowedProductCategoryParents", getAllowedProductCategoryParents(currentStore));
		model.addAttribute("storeProductCategoryTree", storeProductCategoryService.findTree(currentStore));
		model.addAttribute("brands", brandService.findAll());
		model.addAttribute("promotions", promotionService.findList(currentStore, null, true));
		model.addAttribute("productTags", productTagService.findAll());
		model.addAttribute("storeProductTags", storeProductTagService.findList(currentStore, null));
		model.addAttribute("specifications", specificationService.findAll());
		List<MemberRank> memberRankList=memberRankService.findAll();
		model.addAttribute("memberRankList",memberRankList);
		return "business/product/add";
	}

	/**
	 * 保存
	 */
	@PostMapping("/save")
	public String save(@ModelAttribute(name = "productForm") Product productForm,@ModelAttribute(binding = false) ProductCategory productCategory, SkuForm skuForm, SkuListForm skuListForm, Long brandId, Long[] promotionIds, Long[] productTagIds, Long[] storeProductTagIds,
			Long storeProductCategoryId, HttpServletRequest request, @CurrentStore Store currentStore, RedirectAttributes redirectAttributes) {
		if (productForm.getMemberrankId()!=null || productForm.getGradeDistributionFlag()) {
			Business business = currentStore.getBusiness();
			boolean gradeGiftBag = business.getGradeGiftBag();
			if(!gradeGiftBag || !(productForm.getType().equals(Product.Type.general) || productForm.getType().equals(Product.Type.coupon))){
				productForm.setGradeDistributionFlag(false);
				productForm.setMemberrankId(null);
			}
		}
		productImageService.filter(productForm.getProductImages());
			parameterValueService.filter(productForm.getParameterValues());
			specificationItemService.filter(productForm.getSpecificationItems());
			skuService.filter(skuListForm.getSkuList());

			Long productCount = productService.count(null, currentStore, null, null, null, null, null, null);
			if (currentStore.getStoreRank() != null && currentStore.getStoreRank().getQuantity() != null && productCount >= currentStore.getStoreRank().getQuantity()) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
			if (productCategory == null) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
			if (storeProductCategoryId != null) {
				StoreProductCategory storeProductCategory = storeProductCategoryService.find(storeProductCategoryId);
				if (storeProductCategory == null || !currentStore.equals(storeProductCategory.getStore())) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
				productForm.setStoreProductCategory(storeProductCategory);
			}
			productForm.setStore(currentStore);
			productForm.setProductCategory(productCategory);
			productForm.setBrand(brandService.find(brandId));
			productForm.setPromotions(new HashSet<>(promotionService.findList(promotionIds)));
			productForm.setProductTags(new HashSet<>(productTagService.findList(productTagIds)));
			productForm.setStoreProductTags(new HashSet<>(storeProductTagService.findList(storeProductTagIds)));

			productForm.removeAttributeValue();
			for (Attribute attribute : productForm.getProductCategory().getAttributes()) {
				String value = request.getParameter("attribute_" + attribute.getId());
				String attributeValue = attributeService.toAttributeValue(attribute, value);
				productForm.setAttributeValue(attribute, attributeValue);
			}

			if (!isValid(productForm, BaseEntity.Save.class)) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
			if (StringUtils.isNotEmpty(productForm.getSn()) && productService.snExists(productForm.getSn())) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
			if (productForm.hasSpecification()) {
				List<Sku> skus = skuListForm.getSkuList();
				if (CollectionUtils.isEmpty(skus) || !isValid(skus, getValidationGroup(productForm.getType()), BaseEntity.Save.class)) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
				productService.create(productForm, skus);
			} else {
				Sku sku = skuForm.getSku();
				if (sku == null || !isValid(sku, getValidationGroup(productForm.getType()), BaseEntity.Save.class)) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
				productService.create(productForm, sku);
			}

			addFlashMessage(redirectAttributes, SUCCESS_MESSAGE);
		return "redirect:list";
	}

	/**
	 * 编辑
	 */
	@GetMapping("/skuList")
	public String skuList(@ModelAttribute(binding = false) Product product, @CurrentStore Store currentStore, ModelMap model) {
		if (product == null) {
			return UNPROCESSABLE_ENTITY_VIEW;
		}
		List<Map<String,Object>> list = new ArrayList<>();
		for (Sku sku:product.getSkus()){
				Map<String,Object> map =new HashMap<>();
				map.put("id",sku.getId());
				map.put("price",sku.getPrice());
				map.put("stock",sku.getStock());
				map.put("allocatedStock",sku.getAllocatedStock());
			List<SpecificationValue> specificationValues = sku.getSpecificationValues();
			String specification="";
				for (SpecificationValue specificationValue:specificationValues) {
					specification+=specificationValue.getValue()+"      ";
				}
			map.put("specification",specification);
				list.add(map);
		}

		model.addAttribute("skuList", list);
		return "business/product/skuList";
	}


	/**
	 * 编辑
	 */
	@GetMapping("/edit")
	public String edit(@ModelAttribute(binding = false) Product product, @CurrentStore Store currentStore, ModelMap model) {
		if (product == null) {
			return UNPROCESSABLE_ENTITY_VIEW;
		}
		Business business = currentStore.getBusiness();
		boolean gradeGiftBag = business.getGradeGiftBag();
		if (gradeGiftBag) {
			if (Product.Type.general.equals(product.getType()) || Product.Type.coupon.equals(product.getType())) {
				List<MemberRank> memberRankList=memberRankService.findAll();
				model.addAttribute("memberRankList",memberRankList);
			}
		}
		model.addAttribute("gradeGiftBag",gradeGiftBag);
		model.addAttribute("types", Product.Type.values());
		model.addAttribute("productCategoryTree", productCategoryService.findTree());
		model.addAttribute("allowedProductCategories", productCategoryService.findList(currentStore, null, null, null));
		model.addAttribute("allowedProductCategoryParents", getAllowedProductCategoryParents(currentStore));
		model.addAttribute("storeProductCategoryTree", storeProductCategoryService.findTree(currentStore));
		model.addAttribute("brands", brandService.findAll());
		model.addAttribute("promotions", promotionService.findList(currentStore, null, true));
		model.addAttribute("productTags", productTagService.findAll());
		model.addAttribute("storeProductTags", storeProductTagService.findList(currentStore, null));
		model.addAttribute("specifications", specificationService.findAll());
		model.addAttribute("product", product);
		return "business/product/edit";
	}

	/**
	 * 更新
	 */
	@PostMapping("/update")
	public String update(@ModelAttribute("productForm") Product productForm, @ModelAttribute(binding = false) Product product, @ModelAttribute(binding = false) ProductCategory productCategory, SkuForm skuForm, SkuListForm skuListForm, Long brandId, Long[] promotionIds, Long[] productTagIds,
			Long[] storeProductTagIds, Long storeProductCategoryId, HttpServletRequest request, @CurrentStore Store currentStore, RedirectAttributes redirectAttributes) {
		BigDecimal price = product.getPrice();
		BigDecimal rewardDuiBan = product.getRewardDuiBan();
		BigDecimal rewardYongJin = product.getRewardYongJin();
		if (productForm.getMemberrankId()!=null || productForm.getGradeDistributionFlag()) {
			Business business = currentStore.getBusiness();
			boolean gradeGiftBag = business.getGradeGiftBag();
			if(!gradeGiftBag || !(product.getType().equals(Product.Type.general) || product.getType().equals(Product.Type.coupon))){
				productForm.setGradeDistributionFlag(false);
				productForm.setMemberrankId(null);
			}
		}
		if (rewardDuiBan.compareTo(price) <= 0 || rewardYongJin.compareTo(price) <= 0 || (rewardDuiBan.add(rewardYongJin)).compareTo(price) <= 0) {

			productImageService.filter(productForm.getProductImages());
			parameterValueService.filter(productForm.getParameterValues());
			specificationItemService.filter(productForm.getSpecificationItems());
			skuService.filter(skuListForm.getSkuList());
			if (product == null) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
			if (productCategory == null) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
			List<Promotion> promotions = promotionService.findList(promotionIds);
			if (CollectionUtils.isNotEmpty(promotions)) {
				if (currentStore.getPromotions() == null || !currentStore.getPromotions().containsAll(promotions)) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
			}
			if (storeProductCategoryId != null) {
				StoreProductCategory storeProductCategory = storeProductCategoryService.find(storeProductCategoryId);
				if (storeProductCategory == null || !currentStore.equals(storeProductCategory.getStore())) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
				productForm.setStoreProductCategory(storeProductCategory);
			}
			productForm.setId(product.getId());
			productForm.setType(product.getType());
			productForm.setIsActive(true);
			productForm.setProductCategory(productCategory);
			productForm.setBrand(brandService.find(brandId));
			productForm.setPromotions(new HashSet<>(promotions));
			productForm.setProductTags(new HashSet<>(productTagService.findList(productTagIds)));
			productForm.setStoreProductTags(new HashSet<>(storeProductTagService.findList(storeProductTagIds)));

			productForm.removeAttributeValue();
			for (Attribute attribute : productForm.getProductCategory().getAttributes()) {
				String value = request.getParameter("attribute_" + attribute.getId());
				String attributeValue = attributeService.toAttributeValue(attribute, value);
				productForm.setAttributeValue(attribute, attributeValue);
			}

			if (!isValid(productForm, BaseEntity.Update.class)) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}

			if (productForm.hasSpecification()) {
				List<Sku> skus = skuListForm.getSkuList();
				if (CollectionUtils.isEmpty(skus) || !isValid(skus, getValidationGroup(productForm.getType()), BaseEntity.Update.class)) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
				productService.modify(productForm, skus);
			} else {
				Sku sku = skuForm.getSku();
				if (sku == null || !isValid(sku, getValidationGroup(productForm.getType()), BaseEntity.Update.class)) {
					return UNPROCESSABLE_ENTITY_VIEW;
				}
				productService.modify(productForm, sku);
			}
			addFlashMessage(redirectAttributes, SUCCESS_MESSAGE);
		}
		return "redirect:list";
	}

	/**
	 * 列表
	 */
	@GetMapping("/list")
	public String list(@ModelAttribute(binding = false) ProductCategory productCategory, Product.Type type, Long brandId, Long promotionId, Long productTagId, Long storeProductTagId, Boolean isActive, Boolean isMarketable, Boolean isList, Boolean isTop, Boolean isOutOfStock, Boolean isStockAlert,
			Pageable pageable, @CurrentStore Store currentStore, ModelMap model) {
		Brand brand = brandService.find(brandId);
		Promotion promotion = promotionService.find(promotionId);
		ProductTag productTag = productTagService.find(productTagId);
		StoreProductTag storeProductTag = storeProductTagService.find(storeProductTagId);
		if (promotion != null) {
			if (!currentStore.equals(promotion.getStore())) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
		}
		if (storeProductTag != null) {
			if (!currentStore.equals(storeProductTag.getStore())) {
				return UNPROCESSABLE_ENTITY_VIEW;
			}
		}

		model.addAttribute("types", Product.Type.values());
		model.addAttribute("productCategoryTree", productCategoryService.findTree());
		model.addAttribute("allowedProductCategories", productCategoryService.findList(currentStore, null, null, null));
		model.addAttribute("allowedProductCategoryParents", getAllowedProductCategoryParents(currentStore));
		model.addAttribute("brands", brandService.findAll());
		model.addAttribute("promotions", promotionService.findList(currentStore, null, true));
		model.addAttribute("productTags", productTagService.findAll());
		model.addAttribute("storeProductTags", storeProductTagService.findList(currentStore, true));
		model.addAttribute("type", type);
		model.addAttribute("productCategoryId", productCategory != null ? productCategory.getId() : null);
		model.addAttribute("brandId", brandId);
		model.addAttribute("promotionId", promotionId);
		model.addAttribute("productTagId", productTagId);
		model.addAttribute("storeProductTagId", storeProductTagId);
		model.addAttribute("isMarketable", isMarketable);
		model.addAttribute("isList", isList);
		model.addAttribute("isTop", isTop);
		model.addAttribute("isActive", isActive);
		model.addAttribute("isOutOfStock", isOutOfStock);
		model.addAttribute("isStockAlert", isStockAlert);
		model.addAttribute("page", productService.findPage(type, currentStore, productCategory, null, brand, promotion, productTag, storeProductTag, null, null, null, isMarketable, isList, isTop, isActive, isOutOfStock, isStockAlert, null, null, pageable));
		return "business/product/list";
	}

	/**
	 * 删除
	 */
	@PostMapping("/delete")
	public ResponseEntity<?> delete(Long[] ids, @CurrentStore Store currentStore) {
		for (Long id : ids) {
			Product product = productService.find(id);
			if (product == null) {
				return Results.UNPROCESSABLE_ENTITY;
			}
			if (!currentStore.equals(product.getStore())) {
				return Results.UNPROCESSABLE_ENTITY;
			}
			productService.delete(product.getId());
		}
		return Results.OK;
	}

	/**
	 * 上架商品
	 */
	@PostMapping("/shelves")
	public ResponseEntity<?> shelves(Long[] ids, @CurrentStore Store currentStore) {
		if (ids == null) {
			return Results.UNPROCESSABLE_ENTITY;
		}
		for (Long id : ids) {
			Product product = productService.find(id);
			if (product == null) {
				return Results.UNPROCESSABLE_ENTITY;
			}
			if (!currentStore.equals(product.getStore())) {
				return Results.UNPROCESSABLE_ENTITY;
			}
			if (!currentStore.getProductCategories().contains(product.getProductCategory())) {
				return Results.unprocessableEntity("business.product.isNotMarketable");
			}
			if (!product.getIsMarketable()) {
				product.setIsMarketable(true);
				productService.update(product);
			}
		}
		return Results.OK;
	}

	/**
	 * 下架商品
	 */
	@PostMapping("/shelf")
	public ResponseEntity<?> shelf(Long[] ids, @CurrentStore Store currentStore) {
		if (ids == null) {
			return Results.UNPROCESSABLE_ENTITY;
		}
		for (Long id : ids) {
			Product product = productService.find(id);
			if (product == null) {
				return Results.UNPROCESSABLE_ENTITY;
			}
			if (!currentStore.equals(product.getStore())) {
				return Results.UNPROCESSABLE_ENTITY;
			}
			if (product.getIsMarketable()) {
				product.setIsMarketable(false);
				productService.update(product);
			}
		}
		return Results.OK;
	}

	/**
	 * 获取允许发布商品分类上级分类
	 *
	 * @param store
	 *            店铺
	 * @return 允许发布商品分类上级分类
	 */
	private Set<ProductCategory> getAllowedProductCategoryParents(Store store) {
		Assert.notNull(store);

		Set<ProductCategory> result = new HashSet<>();
		List<ProductCategory> allowedProductCategories = productCategoryService.findList(store, null, null, null);
		for (ProductCategory allowedProductCategory : allowedProductCategories) {
			result.addAll(allowedProductCategory.getParents());
		}
		return result;
	}

	/**
	 * 根据类型获取验证组
	 *
	 * @param type
	 *            类型
	 * @return 验证组
	 */
	private Class<?> getValidationGroup(Product.Type type) {
		Assert.notNull(type);

		switch (type) {
		case general:
			return Sku.General.class;
		case exchange:
			return Sku.Exchange.class;
		case gift:
			return Sku.Gift.class;
			case coupon:
				return Sku.Coupon.class;
		}
		return null;
	}

	/**
	 * FormBean - SKU
	 *
	 * @author SHOP++ Team
	 * @version 5.0
	 */
	public static class SkuForm {

		/**
		 * SKU
		 */
		private Sku sku;

		/**
		 * 获取SKU
		 *
		 * @return SKU
		 */
		public Sku getSku() {
			return sku;
		}

		/**
		 * 设置SKU
		 *
		 * @param sku
		 *            SKU
		 */
		public void setSku(Sku sku) {
			this.sku = sku;
		}

	}

	/**
	 * FormBean - SKU
	 *
	 * @author SHOP++ Team
	 * @version 5.0
	 */
	public static class SkuListForm {

		/**
		 * SKU
		 */
		private List<Sku> skuList;

		/**
		 * 获取SKU
		 *
		 * @return SKU
		 */
		public List<Sku> getSkuList() {
			return skuList;
		}

		/**
		 * 设置SKU
		 *
		 * @param skuList
		 *            SKU
		 */
		public void setSkuList(List<Sku> skuList) {
			this.skuList = skuList;
		}

	}

}