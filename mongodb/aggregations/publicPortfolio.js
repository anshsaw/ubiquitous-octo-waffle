'use strict';

/**
 * PUBLIC PORTFOLIO RESOLUTION - /portfolio/:username
 * ==================================================
 *
 * This is the ONLY unauthenticated read path in the product, and the one most
 * likely to be hit by crawlers, link previews and recruiters. It must be fast
 * and must never leak private data.
 *
 * The pipeline assembles the whole page in ONE round trip:
 *
 *   portfolios (indexed unique lookup)
 *        |-- $lookup profiles   (1 doc, by userId)
 *        |-- $lookup projects   (only opted-in, non-deleted, ordered)
 *        |-- $lookup template   (1 doc, by templateId)
 *        `-- $project           (whitelist - strips passwordHash, emails, ids)
 *
 * SECURITY: the final `$project` is an explicit ALLOW-LIST. Never switch it to
 * an exclusion projection - a future field added to `profiles` would then leak
 * to the public internet by default.
 */

/**
 * @param {string} username lowercase handle from the URL
 */
function publicPortfolioPipeline(username) {
  return [
    // 1. Single indexed hit on the partial unique index uniq_published_username.
    { $match: { username, isPublished: true, deleted: false } },
    { $limit: 1 },

    // 2. Owner profile (1:1).
    {
      $lookup: {
        from: 'profiles',
        localField: 'userId',
        foreignField: 'userId',
        as: 'profile',
      },
    },
    { $unwind: { path: '$profile', preserveNullAndEmptyArrays: false } },

    // 3. Template metadata (which React component + default theme).
    {
      $lookup: {
        from: 'portfolioTemplates',
        localField: 'templateId',
        foreignField: '_id',
        as: 'template',
      },
    },
    { $unwind: { path: '$template', preserveNullAndEmptyArrays: true } },

    // 4. Projects: filtered inside the sub-pipeline so private/deleted ones
    //    never leave the server, then re-ordered to match orderedProjects.
    {
      $lookup: {
        from: 'projects',
        let: { uid: '$userId', ordered: '$orderedProjects' },
        pipeline: [
          {
            $match: {
              $expr: {
                $and: [
                  { $eq: ['$userId', '$$uid'] },
                  { $eq: ['$deleted', false] },
                  { $eq: ['$includeInPortfolio', true] },
                  { $in: ['$_id', '$$ordered'] },
                ],
              },
            },
          },
          {
            // rank = position in orderedProjects, so job-relevant projects lead
            $addFields: { _rank: { $indexOfArray: ['$$ordered', '$_id'] } },
          },
          { $sort: { _rank: 1 } },
          {
            $project: {
              _id: 1,
              title: 1,
              description: 1,
              techStack: 1,
              repositoryUrl: 1,
              liveDemoUrl: 1,
              imageUrl: 1,
              images: 1,
              role: 1,
              features: 1,
              achievements: 1,
              startDate: 1,
              endDate: 1,
              featured: 1,
            },
          },
        ],
        as: 'projects',
      },
    },

    // 5. Explicit public allow-list.
    {
      $project: {
        _id: 0,
        username: 1,
        publishedAt: 1,
        templateKey: { $ifNull: ['$templateKey', '$template.templateKey'] },
        theme: {
          primaryColor: { $ifNull: ['$theme.primaryColor', '$template.theme.primaryColor'] },
          accentColor: { $ifNull: ['$theme.accentColor', '$template.theme.accentColor'] },
          darkMode: { $ifNull: ['$theme.darkMode', '$template.theme.darkMode'] },
        },
        sections: 1,
        sectionOrder: 1,
        resumeId: 1,

        owner: {
          fullName: '$profile.fullName',
          // portfolio-level override wins, profile value is the fallback
          professionalTitle: { $ifNull: ['$headlineOverride', '$profile.professionalTitle'] },
          bio: { $ifNull: ['$summaryOverride', '$profile.bio'] },
          location: '$profile.location',
          avatarUrl: '$profile.avatarUrl',
          // NOTE: contact.phone and the account email are deliberately omitted.
          // Only what the user explicitly published is exposed.
          contact: {
            publicEmail: '$profile.contact.publicEmail',
            website: '$profile.contact.website',
            github: '$profile.contact.github',
            linkedin: '$profile.contact.linkedin',
            twitter: '$profile.contact.twitter',
          },
        },

        // Skills reordered to match orderedSkills; skills the user chose not to
        // list on this portfolio are dropped entirely.
        // Requires MongoDB >= 5.2 for $sortArray - on older servers, return the
        // raw skills plus orderedSkills and sort in the application layer.
        skills: {
          $let: {
            vars: { ordered: { $ifNull: ['$orderedSkills', []] } },
            in: {
              $map: {
                input: {
                  $sortArray: {
                    input: {
                      $map: {
                        input: {
                          $filter: {
                            input: { $ifNull: ['$profile.skills', []] },
                            as: 's',
                            cond: { $in: ['$$s.normalizedName', '$$ordered'] },
                          },
                        },
                        as: 's',
                        in: {
                          name: '$$s.name',
                          proficiency: '$$s.proficiency',
                          category: '$$s.category',
                          rank: { $indexOfArray: ['$$ordered', '$$s.normalizedName'] },
                        },
                      },
                    },
                    sortBy: { rank: 1 },
                  },
                },
                as: 's',
                in: { name: '$$s.name', proficiency: '$$s.proficiency', category: '$$s.category' },
              },
            },
          },
        },

        education: {
          $map: {
            input: '$profile.education',
            as: 'e',
            in: {
              degree: '$$e.degree',
              institution: '$$e.institution',
              fieldOfStudy: '$$e.fieldOfStudy',
              startYear: '$$e.startYear',
              endYear: '$$e.endYear',
              grade: '$$e.grade',
            },
          },
        },

        experience: {
          $map: {
            input: '$profile.experience',
            as: 'x',
            in: {
              company: '$$x.company',
              role: '$$x.role',
              employmentType: '$$x.employmentType',
              location: '$$x.location',
              startDate: '$$x.startDate',
              endDate: '$$x.endDate',
              description: '$$x.description',
              technologies: '$$x.technologies',
            },
          },
        },

        certificates: {
          $map: {
            input: '$profile.certificates',
            as: 'c',
            in: {
              name: '$$c.name',
              issuingOrganization: '$$c.issuingOrganization',
              issueDate: '$$c.issueDate',
              credentialUrl: '$$c.credentialUrl',
            },
          },
        },

        projects: 1,
      },
    },
  ];
}

/**
 * SIMPLER, FASTER ALTERNATIVE
 * ---------------------------
 * The pipeline above is one round trip but is heavy on `$lookup`. For a public
 * page served behind a CDN, three tiny indexed `find()` calls are often better:
 * they are individually cacheable and trivially debuggable.
 *
 * Benchmark both against your own traffic before choosing. Do not assume the
 * single-pipeline version wins just because it is one call.
 */
async function fetchPublicPortfolioSimple(db, username) {
  const portfolio = await db
    .collection('portfolios')
    .findOne({ username, isPublished: true, deleted: false });
  if (!portfolio) return null;

  const [profile, projects] = await Promise.all([
    db.collection('profiles').findOne({ userId: portfolio.userId }),
    db
      .collection('projects')
      .find({
        userId: portfolio.userId,
        deleted: false,
        includeInPortfolio: true,
        _id: { $in: portfolio.orderedProjects || [] },
      })
      .toArray(),
  ]);

  const rank = new Map((portfolio.orderedProjects || []).map((id, i) => [String(id), i]));
  projects.sort((a, b) => (rank.get(String(a._id)) ?? 999) - (rank.get(String(b._id)) ?? 999));

  return { portfolio, profile, projects };
}

/** Fire-and-forget view counter. Not awaited on the render path. */
function incrementViewCount(db, portfolioId) {
  return db
    .collection('portfolios')
    .updateOne({ _id: portfolioId }, { $inc: { viewCount: 1 } })
    .catch(() => {
      /* analytics must never break page rendering */
    });
}

module.exports = { publicPortfolioPipeline, fetchPublicPortfolioSimple, incrementViewCount };
