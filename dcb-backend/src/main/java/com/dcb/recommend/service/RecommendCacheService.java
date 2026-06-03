package com.dcb.recommend.service;

import com.dcb.common.exception.BizException;
import com.dcb.recommend.dto.RecommendQueryDTO;
import com.dcb.recommend.vo.RecommendCacheVO;
import com.dcb.recommend.vo.RecommendResultVO.NumberGroupVO;
import com.dcb.common.config.service.ConfigService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 推荐号码缓存服务：负责全量计算并缓存号码组合
 * <p>
 * 将 @Cacheable 逻辑独立为一个 Service，确保通过 Spring AOP 代理调用时缓存生效，
 * 避免原 RecommendService 中 @Lazy @Autowired 自注入的 hack 写法。
 */
@Service
public class RecommendCacheService {

    private final ConfigService configService;

    public RecommendCacheService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 全量计算并缓存，相同条件只计算一次
     *
     * @param cacheKey 缓存 key（由调用方通过 {@link #buildCacheKey} 生成）
     * @param dto      过滤条件
     * @return 全量号码组合缓存对象
     */
    @Cacheable(cacheNames = "recommend", key = "#cacheKey")
    public RecommendCacheVO computeAllGroups(String cacheKey, RecommendQueryDTO dto) {
        Set<Integer> excludeRedSet = dto.getExcludeRed() != null
                ? new HashSet<>(dto.getExcludeRed()) : new HashSet<>();
        List<Integer> includeBlue = dto.getIncludeBlue();
        int[] zone = parseRatio3(dto.getZoneRatio());
        int[] oddEven = parseRatio2(dto.getOddEvenRatio());

        List<int[]> filteredReds = new ArrayList<>();
        int[] combo = new int[6];
        enumerateReds(1, 0, combo, filteredReds, dto, excludeRedSet, zone, oddEven);

        List<NumberGroupVO> allGroups = new ArrayList<>();
        long total = 0;
        int maxResult = configService.getInt("recommend.max.result");
        boolean truncated = false;
        outer:
        for (int[] reds : filteredReds) {
            for (int blue : includeBlue) {
                total++;
                if (total > maxResult) {
                    truncated = true;
                    break outer;
                }
                List<Integer> redList = new ArrayList<>(6);
                for (int r : reds) redList.add(r);
                allGroups.add(NumberGroupVO.builder().red(redList).blue(blue).build());
            }
        }
        return new RecommendCacheVO(total - 1, truncated, Collections.unmodifiableList(allGroups));
    }

    /**
     * 将查询条件（不含分页）序列化为稳定字符串，作为缓存 key
     * excludeRed 和 includeBlue 排序后拼接，保证顺序不同但内容相同的请求命中同一缓存
     */
    public String buildCacheKey(RecommendQueryDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("sum:").append(dto.getSumMin()).append("-").append(dto.getSumMax());
        sb.append("|zone:").append(dto.getZoneRatio());
        sb.append("|oe:").append(dto.getOddEvenRatio());

        if (dto.getExcludeRed() != null && !dto.getExcludeRed().isEmpty()) {
            List<Integer> sorted = new ArrayList<>(new TreeSet<>(dto.getExcludeRed()));
            sb.append("|ex:").append(sorted);
        }
        if (dto.getIncludeBlue() != null && !dto.getIncludeBlue().isEmpty()) {
            List<Integer> sorted = new ArrayList<>(new TreeSet<>(dto.getIncludeBlue()));
            sb.append("|blue:").append(sorted);
        }
        // 最大结果数变更时缓存自动失效
        sb.append("|max:").append(configService.getInt("recommend.max.result"));
        return sb.toString();
    }

    // ==================== 算法私有方法 ====================

    private void enumerateReds(int start, int depth, int[] combo,
                               List<int[]> result,
                               RecommendQueryDTO dto,
                               Set<Integer> excludeRed,
                               int[] zone, int[] oddEven) {
        if (depth == 6) {
            if (matchesRedFilters(combo, dto, zone, oddEven)) {
                result.add(Arrays.copyOf(combo, 6));
            }
            return;
        }
        for (int n = start; n <= 33; n++) {
            if (excludeRed.contains(n)) continue;
            combo[depth] = n;
            enumerateReds(n + 1, depth + 1, combo, result, dto, excludeRed, zone, oddEven);
        }
    }

    private boolean matchesRedFilters(int[] combo, RecommendQueryDTO dto, int[] zone, int[] oddEven) {
        if (dto.getSumMin() != null || dto.getSumMax() != null) {
            int sum = 0;
            for (int n : combo) sum += n;
            if (dto.getSumMin() != null && sum < dto.getSumMin()) return false;
            if (dto.getSumMax() != null && sum > dto.getSumMax()) return false;
        }
        if (zone != null) {
            int low = 0, mid = 0, high = 0;
            for (int n : combo) {
                if (n <= 11) low++;
                else if (n <= 22) mid++;
                else high++;
            }
            if (low != zone[0] || mid != zone[1] || high != zone[2]) return false;
        }
        if (oddEven != null) {
            int odd = 0, even = 0;
            for (int n : combo) {
                if (n % 2 == 1) odd++;
                else even++;
            }
            if (odd != oddEven[0] || even != oddEven[1]) return false;
        }
        return true;
    }

    private int[] parseRatio3(String ratio) {
        if (ratio == null || ratio.trim().isEmpty()) return null;
        String[] parts = ratio.trim().split(":");
        if (parts.length != 3) throw new BizException("区间比格式错误，应为 低:中:高，如 2:2:2");
        int[] r = new int[3];
        for (int i = 0; i < 3; i++) {
            try { r[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException e) { throw new BizException("区间比格式错误，应为整数"); }
            if (r[i] < 0) throw new BizException("区间比各项不能为负数");
        }
        if (r[0] + r[1] + r[2] != 6) throw new BizException("区间比三项之和必须等于6");
        return r;
    }

    private int[] parseRatio2(String ratio) {
        if (ratio == null || ratio.trim().isEmpty()) return null;
        String[] parts = ratio.trim().split(":");
        if (parts.length != 2) throw new BizException("奇偶比格式错误，应为 奇:偶，如 3:3");
        int[] r = new int[2];
        for (int i = 0; i < 2; i++) {
            try { r[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException e) { throw new BizException("奇偶比格式错误，应为整数"); }
            if (r[i] < 0) throw new BizException("奇偶比各项不能为负数");
        }
        if (r[0] + r[1] != 6) throw new BizException("奇偶比两项之和必须等于6");
        return r;
    }
}
