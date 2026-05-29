package com.dcb.recommend.service;

import com.dcb.common.exception.BizException;
import com.dcb.recommend.dto.RecommendQueryDTO;
import com.dcb.recommend.vo.RecommendResultVO;
import com.dcb.recommend.vo.RecommendResultVO.NumberGroupVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 号码推荐服务：根据多维度过滤条件筛选合法号码组合
 */
@Service
public class RecommendService {

    private static final int MAX_RESULT = 10000;

    public RecommendResultVO generate(RecommendQueryDTO dto) {
        // 参数校验
        validateParams(dto);

        Set<Integer> excludeRedSet = dto.getExcludeRed() != null
                ? new HashSet<>(dto.getExcludeRed()) : new HashSet<>();
        List<Integer> includeBlue = dto.getIncludeBlue();

        // 解析区间比
        int[] zone = parseRatio3(dto.getZoneRatio());
        // 解析奇偶比
        int[] oddEven = parseRatio2(dto.getOddEvenRatio());

        // 枚举所有合法红球组合并过滤
        List<int[]> filteredReds = new ArrayList<>();
        int[] combo = new int[6];
        enumerateReds(1, 0, combo, filteredReds, dto, excludeRedSet, zone, oddEven);

        // 组合蓝球，统计总数，截断超限结果
        List<NumberGroupVO> allGroups = new ArrayList<>();
        long total = 0;
        boolean truncated = false;

        for (int[] reds : filteredReds) {
            for (int blue : includeBlue) {
                total++;
                if (total <= MAX_RESULT) {
                    List<Integer> redList = new ArrayList<>(6);
                    for (int r : reds) redList.add(r);
                    allGroups.add(NumberGroupVO.builder().red(redList).blue(blue).build());
                } else {
                    truncated = true;
                }
            }
        }

        // 分页
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 20;
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allGroups.size());
        List<NumberGroupVO> pageList = fromIndex < allGroups.size()
                ? allGroups.subList(fromIndex, toIndex) : new ArrayList<>();

        return RecommendResultVO.builder()
                .total(total)
                .truncated(truncated)
                .list(pageList)
                .build();
    }

    /**
     * 递归枚举 C(33,6) 红球组合，边枚举边过滤
     *
     * @param start      本轮从哪个号码开始枚举，保证组合升序且不重复
     * @param depth      当前已选红球数量（0~6），等于6时表示一组选完，触发过滤
     * @param combo      长度为6的数组，存放当前正在构建的红球组合
     * @param result     收集所有通过过滤的红球组合
     * @param dto        原始查询条件，用于取 sumMin/sumMax 做和值过滤
     * @param excludeRed 需要剔除的红球号码集合，枚举时跳过这些号码
     * @param zone       解析后的区间比 int[3]（低区/中区/高区期望数量），null 表示不过滤
     * @param oddEven    解析后的奇偶比 int[2]（奇数/偶数期望数量），null 表示不过滤
     */
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

    /** 检查红球组合是否满足所有过滤条件 */
    private boolean matchesRedFilters(int[] combo, RecommendQueryDTO dto, int[] zone, int[] oddEven) {
        // 和值过滤
        if (dto.getSumMin() != null || dto.getSumMax() != null) {
            int sum = 0;
            for (int n : combo) sum += n;
            if (dto.getSumMin() != null && sum < dto.getSumMin()) return false;
            if (dto.getSumMax() != null && sum > dto.getSumMax()) return false;
        }

        // 区间比过滤
        if (zone != null) {
            int low = 0, mid = 0, high = 0;
            for (int n : combo) {
                if (n <= 11) low++;
                else if (n <= 22) mid++;
                else high++;
            }
            if (low != zone[0] || mid != zone[1] || high != zone[2]) return false;
        }

        // 奇偶比过滤
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

    /** 解析"低:中:高"格式，返回 int[3]，不填返回 null */
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

    /** 解析"奇:偶"格式，返回 int[2]，不填返回 null */
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

    private void validateParams(RecommendQueryDTO dto) {
        if (dto.getSumMin() != null && dto.getSumMax() != null
                && dto.getSumMin() > dto.getSumMax()) {
            throw new BizException("和值最小值不能大于最大值");
        }
        if (dto.getExcludeRed() != null) {
            for (int n : dto.getExcludeRed()) {
                if (n < 1 || n > 33) throw new BizException("剔除红球号码范围为1~33");
            }
        }
        for (int n : dto.getIncludeBlue()) {
            if (n < 1 || n > 16) throw new BizException("蓝球号码范围为1~16");
        }
    }
}
