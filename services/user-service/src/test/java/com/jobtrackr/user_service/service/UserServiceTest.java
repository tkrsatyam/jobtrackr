package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.dto.ChangePasswordRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.exception.InvalidCurrentPasswordException;
import com.jobtrackr.user_service.exception.PasswordChangeNotAllowedException;
import com.jobtrackr.user_service.exception.UserNotFoundException;
import com.jobtrackr.user_service.repository.RefreshTokenRepository;
import com.jobtrackr.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;
    private UUID userId;
    private User localUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, refreshTokenRepository, passwordEncoder);

        userId = UUID.randomUUID();
        localUser = User.builder()
                .id(userId)
                .email("jane@example.com")
                .password("encoded-old-password")
                .fullName("Jane Doe")
                .avatarUrl("http://avatar.example.com/jane.png")
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .build();
    }

    // ---- getProfile ----

    @Test
    void getProfileReturnsMappedDto() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));

        UserProfileDTO dto = userService.getProfile(userId);

        assertThat(dto.getId()).isEqualTo(userId);
        assertThat(dto.getEmail()).isEqualTo("jane@example.com");
        assertThat(dto.getFullName()).isEqualTo("Jane Doe");
        assertThat(dto.getProvider()).isEqualTo("LOCAL");
    }

    @Test
    void getProfileUserNotFoundThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ---- updateProfile ----

    @Test
    void updateProfileUpdatesFullNameAndAvatar() {
        UserProfileDTO update = UserProfileDTO.builder()
                .fullName("Jane Updated")
                .avatarUrl("http://avatar.example.com/new.png")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileDTO result = userService.updateProfile(userId, update);

        assertThat(result.getFullName()).isEqualTo("Jane Updated");
        assertThat(result.getAvatarUrl()).isEqualTo("http://avatar.example.com/new.png");
    }

    @Test
    void updateProfileUserNotFoundThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(userId, UserProfileDTO.builder().fullName("X").build()))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ---- changePassword ----

    @Test
    void changePasswordWithCorrectOldPasswordSucceeds() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password123");
        request.setConfirmPassword("new-password123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));
        when(passwordEncoder.matches("old-password", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password123")).thenReturn("encoded-new-password");

        userService.changePassword(userId, request);

        verify(userRepository).save(argThat(u -> u.getPassword().equals("encoded-new-password")));
    }

    @Test
    void changePasswordWithWrongOldPasswordThrows() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password123");
        request.setConfirmPassword("new-password123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));
        when(passwordEncoder.matches("wrong-password", "encoded-old-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordGoogleOAuthUserRejected() {
        User googleUser = User.builder()
                .id(userId)
                .email("jane@example.com")
                .provider(User.AuthProvider.GOOGLE)
                .role(User.Role.USER)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("whatever");
        request.setNewPassword("new-password123");
        request.setConfirmPassword("new-password123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(googleUser));

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(PasswordChangeNotAllowedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ---- deleteAccount ----

    @Test
    void deleteAccountDeletesRefreshTokensThenUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));

        userService.deleteAccount(userId);

        verify(refreshTokenRepository).deleteAllByUser(localUser);
        verify(userRepository).delete(localUser);
    }

    @Test
    void deleteAccountUserNotFoundThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount(userId))
                .isInstanceOf(UserNotFoundException.class);

        verify(refreshTokenRepository, never()).deleteAllByUser(any());
    }
}