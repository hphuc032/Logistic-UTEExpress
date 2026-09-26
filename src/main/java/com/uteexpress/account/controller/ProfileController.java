package com.uteexpress.account.controller;

import com.uteexpress.account.dto.AvatarUpload;
import com.uteexpress.account.dto.PasswordChangeForm;
import com.uteexpress.account.dto.ProfileForm;
import com.uteexpress.account.dto.ProfileView;
import com.uteexpress.account.service.ProfileService;
import com.uteexpress.common.storage.StorageException;
import com.uteexpress.common.storage.StoredContent;
import com.uteexpress.identity.service.CurrentPasswordMismatchException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class ProfileController {
    private static final String VIEW = "account/profile";
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @InitBinder("profileForm")
    void restrictProfileFields(WebDataBinder binder) {
        binder.setAllowedFields("fullName", "phone");
    }

    @InitBinder("passwordChangeForm")
    void restrictPasswordFields(WebDataBinder binder) {
        binder.setAllowedFields("currentPassword", "newPassword", "confirmPassword");
    }

    @GetMapping("/user/profile")
    String showProfile(Model model) {
        populate(model, null, null);
        return VIEW;
    }

    @PostMapping("/user/profile")
    String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            populate(model, form, null);
            return VIEW;
        }
        profiles.updateProfile(form);
        redirect.addFlashAttribute("successMessage", "Thông tin cá nhân đã được cập nhật.");
        return "redirect:/user/profile";
    }

    @PostMapping("/user/avatar")
    String updateAvatar(@RequestParam("avatar") MultipartFile avatar, RedirectAttributes redirect) {
        try {
            profiles.updateAvatar(new AvatarUpload(avatar.getBytes(), avatar.getOriginalFilename(),
                    avatar.getContentType()));
            redirect.addFlashAttribute("successMessage", "Ảnh đại diện đã được cập nhật.");
        } catch (StorageException exception) {
            redirect.addFlashAttribute("errorMessage", avatarError(exception));
        } catch (IOException exception) {
            redirect.addFlashAttribute("errorMessage", "Không thể đọc ảnh đã chọn.");
        }
        return "redirect:/user/profile";
    }

    @PostMapping("/user/password")
    String changePassword(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm form,
            BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            populate(model, null, form);
            return VIEW;
        }
        try {
            profiles.changePassword(form.getCurrentPassword(), form.getNewPassword());
        } catch (CurrentPasswordMismatchException exception) {
            binding.rejectValue("currentPassword", "password.current.invalid",
                    "Mật khẩu hiện tại không chính xác.");
            populate(model, null, form);
            return VIEW;
        }
        return "redirect:/login?passwordChanged=true";
    }

    @GetMapping("/user/avatar")
    ResponseEntity<byte[]> currentAvatar() {
        return profiles.currentAvatar()
                .map(ProfileController::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void populate(Model model, ProfileForm submittedProfile, PasswordChangeForm submittedPassword) {
        ProfileView profile = profiles.currentProfile();
        model.addAttribute("profile", profile);
        if (!model.containsAttribute("profileForm")) {
            ProfileForm form = submittedProfile == null ? new ProfileForm() : submittedProfile;
            if (submittedProfile == null) {
                form.setFullName(profile.fullName());
                form.setPhone(profile.phone());
            }
            model.addAttribute("profileForm", form);
        }
        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm",
                    submittedPassword == null ? new PasswordChangeForm() : submittedPassword);
        }
    }

    private static ResponseEntity<byte[]> imageResponse(StoredContent image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    private static String avatarError(StorageException exception) {
        return switch (exception.reason()) {
            case TOO_LARGE -> "Ảnh vượt quá dung lượng cho phép.";
            case UNSUPPORTED_TYPE -> "Định dạng ảnh không được hỗ trợ.";
            default -> "Ảnh không hợp lệ.";
        };
    }
}
