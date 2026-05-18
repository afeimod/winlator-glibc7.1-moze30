#define __NR_accept 202
#define __NR_faccessat2 439
#define __NR_fchmodat2 452
#define __NR_epoll_pwait2 441
#define __NR_statx 291
#define __NR_close_range 436
#define __NR_shmat 196
#define __NR_shmctl 195
#define __NR_shmdt 197
#define __NR_shmget 194
#define __NR_setuid 146
#define __NR_setgid 144
#define __NR_setreuid 145
#define __NR_setregid 143
#define __NR_setresuid 147
#define __NR_setresgid 149
#define __NR_setfsuid 151
#define __NR_setfsgid 152
#define __NR_clone3 435
#define __NR_futex_waitv 449
#define __NR_landlock_create_ruleset 444
#define __NR_pidfd_send_signal 424
#define __NR_rseq 293
#define __NR_set_robust_list 99
#define __NR_get_robust_list 100
#define __NR_io_uring_setup 425
#define __NR_io_uring_enter 426
#define __NR_io_uring_register 427
#define __NR_name_to_handle_at 264
#define __NR_open_by_handle_at 265
#define __NR_kcmp 272
#define __NR_mbind 235
#define __NR_get_mempolicy 236
#define __NR_set_mempolicy 237
#define __NR_mq_open 180
#define __NR_rt_sigreturn 139
#define __NR_semget 190
#define __NR_semctl 191
#define __NR_semop 193
#define __NR_semtimedop 192
#define __NR_msgctl 187
#define __NR_msgget 186
#define __NR_msgrcv 188
#define __NR_msgsnd 189

#define DISABLED_SYSCALL_WITH_FAKESYSCALL \
        case __NR_shmat: \
                return (long int)shmat(a0, (const void *)a1, a2); \
        case __NR_setuid: \
        case __NR_setgid: \
        case __NR_setreuid: \
        case __NR_setregid: \
        case __NR_setresuid: \
        case __NR_setresgid: \
        case __NR_setfsuid: \
        case __NR_setfsgid: \
        case 1008: \
                return 0; \
        case __NR_clone3: \
        case __NR_futex_waitv: \
        case __NR_landlock_create_ruleset: \
        case __NR_pidfd_send_signal: \
        case __NR_rseq: \
        case __NR_set_robust_list: \
        case __NR_get_robust_list: \
        case __NR_io_uring_setup: \
        case __NR_io_uring_enter: \
        case __NR_io_uring_register: \
        case __NR_name_to_handle_at: \
        case __NR_open_by_handle_at: \
        case __NR_kcmp: \
        case __NR_mbind: \
        case __NR_get_mempolicy: \
        case __NR_set_mempolicy: \
        case __NR_mq_open: \
        case __NR_rt_sigreturn: \
        case __NR_semget: \
        case __NR_semctl: \
        case __NR_semop: \
        case __NR_semtimedop: \
        case __NR_msgctl: \
        case __NR_msgget: \
        case __NR_msgrcv: \
        case __NR_msgsnd: \
                return INLINE_SYSCALL_ERROR_RETURN_VALUE(ENOSYS); \
        case __NR_accept: \
                return accept4(a0, (struct sockaddr *)a1, (socklen_t *)a2, 0); \
        case __NR_close_range: \
                return close_range(a0, a1, a2); \
        case __NR_faccessat2: \
                return faccessat(a0, (const char *)a1, a2, a3); \
        case __NR_epoll_pwait2: \
                return fake_epoll_pwait2(a0, (struct epoll_event *)a1, a2, (const struct __timespec64 *)a3, (const __sigset_t *)a4, a5); \
        case __NR_fchmodat2: \
                return fchmodat(a0, (const char*)a1, a2, a3); \
        case __NR_shmctl: \
                return shmctl(a0, a1, (struct shmid_ds *)a2); \
        case __NR_shmdt: \
                return shmdt((const void *)a0); \
        case __NR_shmget: \
                return shmget(a0, a1, a2); \
        case __NR_statx: \
                return statx_generic(a0, (const char *)a1, a2, a3, (struct statx *)a4);